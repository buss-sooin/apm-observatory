# apm-observatory 위키

APM 백엔드를 직접 만들어보면서 JVM 내부 동작과 관측 데이터 파이프라인을 이해하려고 한 프로젝트입니다. 이 위키는 [저장소 README](https://github.com/buss-sooin/apm-observatory)가 요약해 놓은 내용의 근거와 세부 구현을 담고 있습니다.

README를 먼저 읽고, 더 알고 싶은 부분만 아래에서 골라 보시면 됩니다.

## 어디서부터 볼까

**직접 돌려보고 싶다면** → [실행 방법](실행-방법)

Docker Compose로 컨테이너 11개를 띄워 수집부터 조회까지 확인하는 절차와 시연 시나리오를 담았습니다.

**왜 이 기술을 골랐는지 알고 싶다면** → [기술 선택과 그 이유](기술-선택과-그-이유)

바이트코드 조작, 전송, 게이트웨이, 버퍼, 시계열 저장, AI 분석 각 구성요소마다 어떤 후보를 놓고 무엇을 기준으로 골랐는지 정리했습니다.

**데이터가 어떻게 흐르는지 알고 싶다면** → [데이터 흐름과 코드 경로](데이터-흐름과-코드-경로)

Metrics, Traces, Logs, AI 분석 네 갈래를 후킹 지점부터 저장·조회까지 실제 클래스와 메서드 단위로 따라갑니다.

**각 모듈을 어떻게 만들었는지 알고 싶다면** → 아래 모듈별 페이지

| 모듈 | 페이지 | 무엇을 담았나 |
|---|---|---|
| agent | [모듈 설계 · agent](모듈-설계-agent) | 타겟 앱에 부하를 주지 않는 수집·전송 구조 |
| gateway | [모듈 설계 · gateway](모듈-설계-gateway) | gRPC 수신, 인증, Redis Streams 라우팅 |
| collectorserver | [모듈 설계 · collectorserver](모듈-설계-collectorserver) | Streams 소비와 ACK 기반 재처리 |
| aipipeline | [모듈 설계 · aipipeline](모듈-설계-aipipeline) | 룰 기반 이상 감지와 AI 분석 파이프라인 |
| apiserver | [모듈 설계 · apiserver](모듈-설계-apiserver) | JWT 인증과 조회 API 설계 |
| 전체 공통 | [공통 설계 결정](공통-설계-결정) | 패키지 구조, Port & Adapter, 예외 처리 등 |

**만들면서 무엇을 겪었는지 알고 싶다면** → [어려웠던 문제와 해결 과정](어려웠던-문제와-해결-과정)

ClassLoader 위임 모델 때문에 커스텀 Appender가 붙지 않았던 문제, 타임스탬프가 1970년으로 저장된 문제 등을 원인 추적 과정과 함께 남겼습니다.

**어떻게 검증했는지 알고 싶다면** → [테스트 전략](테스트-전략)

---

[저장소로 돌아가기](https://github.com/buss-sooin/apm-observatory)
