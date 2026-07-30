# 모듈 설계 · apiserver

**역할과 주요 코드**

JWT + Spring Security 기반 인증으로 REST API를 제공합니다. Metrics 추세, Span 폭포수 차트, 로그 스트림, AI 분석 결과 조회, 임계값 설정 API를 포함합니다.

- [`apiserver/src/main/java/com/apm/observatory/apiserver/auth/security/SecurityConfig.java`](https://github.com/buss-sooin/apm-observatory/blob/main/apiserver/src/main/java/com/apm/observatory/apiserver/auth/security/SecurityConfig.java)
- [`apiserver/src/main/java/com/apm/observatory/apiserver/metrics/controller/MetricsController.java`](https://github.com/buss-sooin/apm-observatory/blob/main/apiserver/src/main/java/com/apm/observatory/apiserver/metrics/controller/MetricsController.java)

---

수집해서 저장한 데이터를 외부에서 조회하는 모듈입니다. 위젯이나 대시보드 UI 없이 JSON으로 응답하는 환경이라, 응답 형태가 데이터를 그대로 읽을 수 있을 만큼 명확해야 한다고 생각했습니다. Metrics와 Logs는 원형 그대로 노출하고, Spans는 한 요청 안의 여러 Span을 평면적으로 나열하면 호출 관계가 안 보이니 TraceID 기준으로 트리 구조로 조립하고 각 Span의 시작 오프셋(`offsetMs`)과 깊이(`depth`)를 함께 내려서 폭포수 형태로 읽힐 수 있게 했습니다.

AI 분석 결과는 aipipeline이 이미 결과와 근거(evidence)를 분리해 저장해둔 상태라, API는 그 중 AI 결과 본문만 조회해서 그대로 노출합니다. evidence를 같이 노출하려면 룰 계산을 재현하거나 근거 데이터를 모듈 간에 끌어오는 의존성이 필요한데, 그 의존성을 common 모듈에 얹기에는 부담이 컸습니다. 따라서 결과만을 보여주는 구성을 선택했습니다.

제공하는 엔드포인트는 다음과 같습니다.

| 엔드포인트 | 용도 |
|---|---|
| `GET /metrics/current` | 앱의 최신 자원 스냅샷 |
| `GET /metrics/trend` | 시간 범위 내 자원 시계열 |
| `GET /metrics/summary` | 구간 집계 + 기울기 + 임계값 대비 수준 |
| `GET /spans/waterfall` | TraceID 기준 Span 트리 (offsetMs, depth 포함) |
| `GET /logs/stream` | 시간 범위 내 로그 (level 필터 선택) |
| `GET /ai/results` | AI 분석 결과 목록 |
| `GET /ai/results/{id}` | AI 분석 결과 단건 |
| `POST /auth/login` | JWT 발급 |
| `POST /config/threshold` | 임계값 설정 (ADMIN) |
| `POST/DELETE /config/business-cycle` | 비즈니스 사이클 설정/삭제 (ADMIN) |

인증과 권한은 Spring Security와 JWT의 기본 구성을 그대로 따랐습니다. 로그인 시 JWT를 발급하고, 이후 요청은 Authorization 헤더로 검증하며, 설정 변경은 ADMIN 역할로 제한합니다. 외부 API 모듈에서 갖춰야 할 기본을 표준 방식으로 챙기는 정도이고, 별도의 결정이 들어간 자리는 아닙니다.

- [`apiserver/src/main/java/com/apm/observatory/apiserver/span/controller/SpanController.java`](https://github.com/buss-sooin/apm-observatory/blob/main/apiserver/src/main/java/com/apm/observatory/apiserver/span/controller/SpanController.java)
- [`apiserver/src/main/java/com/apm/observatory/apiserver/ai/controller/AiResultController.java`](https://github.com/buss-sooin/apm-observatory/blob/main/apiserver/src/main/java/com/apm/observatory/apiserver/ai/controller/AiResultController.java)
- [`apiserver/src/main/java/com/apm/observatory/apiserver/auth/`](https://github.com/buss-sooin/apm-observatory/blob/main/apiserver/src/main/java/com/apm/observatory/apiserver/auth/)

---

[← 위키 홈](Home)
