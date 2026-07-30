# 모듈 설계 · collectorserver

**역할과 주요 코드**

Redis Streams를 Consumer Group으로 소비해서 TimescaleDB에 저장합니다. Metrics는 Disk IO 누적값 계산, Spans는 INTERNAL Span 파생 계산, Logs는 가공 없이 저장합니다.

- [`collectorserver/src/main/java/com/apm/observatory/collectorserver/consumer/AbstractStreamConsumer.java`](https://github.com/buss-sooin/apm-observatory/blob/main/collectorserver/src/main/java/com/apm/observatory/collectorserver/consumer/AbstractStreamConsumer.java)
- [`collectorserver/src/main/java/com/apm/observatory/collectorserver/processor/`](https://github.com/buss-sooin/apm-observatory/blob/main/collectorserver/src/main/java/com/apm/observatory/collectorserver/processor/)

---

수집서버를 만들 때는 Metrics, Spans, Logs 3종의 raw data가 어떤 모습으로 저장되어야 하는지부터 떠올렸습니다. Metrics는 단일 지표로 원자화되는 형태이고, Spans는 한 요청 안에서 부모-자식 관계로 묶이는 계층 구조이며, Logs는 시간순으로 쌓이는 히스토리 성격입니다.

형태가 다른 만큼 저장 처리도 갈립니다. Metrics와 Logs는 도착한 메시지를 스키마에 맞춰 바로 저장하지만, Spans는 같은 요청의 Span이 모여야 계층이 성립하므로 버퍼에 모았다가 저장합니다. 반면 Redis Streams에서 메시지를 꺼내 오는 수집 흐름은 종류와 무관하게 같습니다. 그래서 공통 수집 골격은 추상 클래스에 두고, 종류별로 다른 저장 처리는 Template Method Pattern으로 구현 클래스가 채우도록 분리했습니다.

- [`collectorserver/src/main/java/com/apm/observatory/collectorserver/consumer/AbstractStreamConsumer.java`](https://github.com/buss-sooin/apm-observatory/blob/main/collectorserver/src/main/java/com/apm/observatory/collectorserver/consumer/AbstractStreamConsumer.java)

---

**[collectorserver] ACK 기반 재처리**

모니터링은 관측의 영역입니다. 어느 시점에 무엇이 일어났는지 추적할 수 있어야 의미가 있고, 그러려면 시간 축 위에 끊김 없는 연속된 데이터가 남아있어야 합니다. 중간에 추적이 끊기면 그 시점의 자원 사용량, 호출 흐름, 로그가 함께 사라져 추적이 불가능해지기 때문에 유실이 없도록 해야 한다고 생각했습니다.

Redis Streams는 새로운 메시지를 끝에 덧붙이기만 할 수 있는 로그 구조이며, 메시지 ACK와 Consumer Group을 기본으로 제공합니다([Redis 공식 — Streams](https://redis.io/docs/latest/develop/data-types/streams/)). Consumer Group이 메시지를 소비하면 PEL(Pending Entry List)에 기록되고, 처리한 결과를 ACK로 보내야 PEL에서 제거됩니다. 처리에 실패하면 ACK 없이 PEL에 남아 다음 폴링에서 다시 시도할 수 있습니다. 수집서버는 DB 저장까지 성공한 뒤에만 ACK를 보내도록 두어 유실 가능성을 차단했습니다.

---

**[collectorserver] SpanProcessor**

수집서버의 Metrics와 Logs는 들어온 raw data를 스키마에 맞춰 그대로 저장하면 되지만, Spans는 한 요청 안에서 여러 Span이 부모-자식 관계로 묶이는 계층 구조라 같은 TraceID끼리 모아 처리해야 한다고 생각했습니다.

후킹 범위와 기준은 임의로 정했습니다. 실제 APM이라면 내부 처리까지 놓치지 않고 계측하겠지만, Span이 계층 구조를 표현할 수 있고 탐지 범위가 명확해지도록 나름의 도식을 잡아 세 지점을 정했습니다. DispatcherServlet을 ROOT로 두고, PreparedStatement는 DB, RestClient는 EXTERNAL로 분류했습니다. 이 세 지점만 후킹하면 한 요청에서 측정되는 건 전체 응답시간(ROOT)과 외부 호출 시간(DB, EXTERNAL)뿐이고, 비즈니스 로직 처리 시간은 어느 후킹에서도 잡히지 않습니다.

측정되지 않은 시간을 그대로 두지 않고 INTERNAL이라는 이름으로 파생 계산해 채워넣기로 했습니다. 계산식은 단순합니다.

```
INTERNAL duration = ROOT duration - sum(DB) - sum(EXTERNAL)
```

이 계산이 성립하려면 같은 TraceID의 ROOT, DB, EXTERNAL Span이 모두 도착해야 합니다. 이 프로젝트의 에이전트는 Span이 종료되는 시점마다 게이트웨이로 전송하는 구조라, 같은 TraceID 묶음이 수집서버에 한 번에 도착하지 않습니다. TraceID별로 Span을 모아두는 버퍼(`TraceBuffer`)를 두고, 일정 시간이 지나면 그 시점까지 모인 Span으로 INTERNAL을 계산해 한꺼번에 저장하는 방식을 택했습니다. 파생 계산이라는 구조에서는 한 TraceID가 언제 끝나는지 알 수 없어 시간으로 판정합니다. 마지막 Span이 도착하고 10초 동안 추가 Span이 없으면 끝난 것으로 보고 저장하며, 첫 Span이 도착하고 60초가 지나면 조건과 무관하게 그때까지 모인 Span만 저장합니다.

- [`collectorserver/src/main/java/com/apm/observatory/collectorserver/processor/SpanProcessor.java`](https://github.com/buss-sooin/apm-observatory/blob/main/collectorserver/src/main/java/com/apm/observatory/collectorserver/processor/SpanProcessor.java)

---

[← 위키 홈](Home)
