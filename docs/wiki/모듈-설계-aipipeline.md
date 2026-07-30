# 모듈 설계 · aipipeline

**역할과 주요 코드**

스케줄러가 주기적으로 TimescaleDB에서 데이터를 읽어 세 가지 룰 기반 이상 감지를 수행합니다. 감지된 결과를 Ollama에 전달해 자연어 분석 결과와 권고를 받아 저장합니다.

- [`aipipeline/src/main/java/com/apm/observatory/aipipeline/scheduler/PerformanceMonitoringScheduler.java`](https://github.com/buss-sooin/apm-observatory/blob/main/aipipeline/src/main/java/com/apm/observatory/aipipeline/scheduler/PerformanceMonitoringScheduler.java)
- [`aipipeline/src/main/java/com/apm/observatory/aipipeline/context/pipeline/PerformanceAnalysisPipelineContext.java`](https://github.com/buss-sooin/apm-observatory/blob/main/aipipeline/src/main/java/com/apm/observatory/aipipeline/context/pipeline/PerformanceAnalysisPipelineContext.java)

---

AI를 어떻게 쓸지 먼저 정했습니다. 이상을 감지하는 데 쓰는 게 아니라, 감지된 결과를 받아 권고를 생성하는 데 쓰기로 했습니다. 감지 방식 자체에는 모니터링 전용으로 설계된 모델, 수학적 시계열 모델, 데이터의 고유 패턴 인식 등 여러 길이 있었지만 모두 모델이나 알고리즘이 판단의 주체가 되는 방식이고, 개인 PC에서 돌리는 오픈소스 모델 규모로는 복합적인 근거를 유추해 결론을 내는 판단을 맡기기 어려웠습니다. 판단의 근거를 좁힐 수 있는 정제된 데이터를 주고 결론을 내게 하는 방식이 모델이 가장 안정적으로 답할 수 있는 형태였고, 그래서 감지는 코드가, 권고는 AI가 맡는 구조로 정했습니다.

이상 감지 규칙은 일종의 도메인 로직이라 스트림으로 전달받은 데이터를 즉시 저장하는 수집서버의 역할과 책임에서 분리되는 게 맞다고 봤습니다. 모니터링이라는 분야를 깊게 다뤄본 경험이 없어 단정하긴 어렵지만, 관측 데이터를 빠르게 모아 저장하고 즉시 제공하는 흐름이 모니터링의 중심이라고 생각했고, 그 흐름에 부가 연산을 끼워 넣어 저장 경로를 늘이고 싶지 않았습니다. 또 모델 호출은 응답 시간과 안정성이 일반 코드와 다르게 흔들리는 구간이라 분리해두면 AI 쪽에서 문제가 생겨도 수집과 제공의 기본 흐름에 전파되지 않습니다. 이런 이유로 aipipeline을 별도 모듈로 설계했습니다. 권고 결과를 외부에 노출하는 API 호출은 apiserver가 담당합니다.

- [`aipipeline/src/main/java/com/apm/observatory/aipipeline/scheduler/PerformanceMonitoringScheduler.java`](https://github.com/buss-sooin/apm-observatory/blob/main/aipipeline/src/main/java/com/apm/observatory/aipipeline/scheduler/PerformanceMonitoringScheduler.java)
- [`aipipeline/src/main/java/com/apm/observatory/aipipeline/ai/service/OllamaAnalysisService.java`](https://github.com/buss-sooin/apm-observatory/blob/main/aipipeline/src/main/java/com/apm/observatory/aipipeline/ai/service/OllamaAnalysisService.java)

---

**[aipipeline] 룰 기반 이상 감지**

이상 감지 규칙은 두 기준으로 구분했습니다. 원인의 위치(앱 자원 내부 vs 외부 의존성)와 변화의 속도(즉각 급등 vs 점진 상승). 이 두 기준의 조합으로 시스템 장애 상태를 세 가지 패턴으로 표현했습니다.

- **즉각적인 이상 신호 (Collapse)** — 자원과 응답시간이 동시에 급등하는 패턴
- **점진적인 이상 신호 (Erosion)** — 자원과 응답시간이 완만하게 같은 방향으로 상승하는 패턴
- **외부 영향의 이상 신호 (External Impact)** — 앱 자원은 정상인데 외부 API 응답시간만 평소 대비 늘어난 패턴

---

**규칙 도출의 구조**

```
이상 원인의 위치
├─ 내부 (앱 자원)
│   ├─ 자원 (cpu, heap)
│   │    ├─ 즉각 급등   →  즉각적인 이상 신호 (Collapse)
│   │    └─ 점진 상승   →  점진적인 이상 신호 (Erosion)
│   └─ 응답시간 (전체 Span)
│        ├─ 즉각 지연   →  즉각적인 이상 신호 (Collapse)
│        └─ 점진 상승   →  점진적인 이상 신호 (Erosion)
└─ 외부 (외부 의존성)
    └─ 외부 응답시간 (EXTERNAL Span)
         └─ 평소 대비 지연 → 외부 영향의 이상 신호 (External Impact)
                            (내부 자원 정상 전제)
```

---

**상태 표현**

각 측정 요소의 결과를 정상/비정상 이분값으로 두면 표현할 수 있는 경우의 수가 부족합니다. 측정값이 임계 안에 머무르는 정상, 임계를 넘어선 이상, 데이터를 못 모아 판정이 성립하지 않는 상태는 서로 다른 의미를 갖고 이후 조합 단계에서도 분기가 달라야 합니다. 표현 단위를 enum으로 두어 각 측정 요소가 가질 수 있는 값을 명시했습니다.

측정 영역별로 enum을 분리했습니다.

- `ResourceStatus` — 자원 측정값(cpu, heap)의 즉각 상태를 분류
- `ResponseStatus` — 응답시간 측정값(전체 Span 또는 외부 Span)의 즉각 상태를 분류
- `TrendStatus` — 시계열 기울기 기반 추세 상태를 분류 (점진 상승 판정 전용)
- `DetectionStatus` — 위 세 enum의 조합으로 도달하는 최종 이상 판정 결과

```java
enum ResourceStatus {
    SPIKED,    // 측정값이 임계 초과 — 이상 상태
    NORMAL,    // 측정값이 임계 이하 — 정상 상태
    NODATA     // 측정 데이터 없음 — 판정 불가 상태
}

enum ResponseStatus {
    SLOWED,    // 측정값이 임계 초과 — 지연 발생
    NORMAL,    // 측정값이 임계 이하 — 정상 응답
    NODATA     // 측정 데이터 없음 — 판정 불가 상태
}

enum TrendStatus {
    RISING,    // 시계열 기울기가 양수 임계 초과 — 점진 상승 중
    FLAT,      // 시계열 기울기가 양수 임계 이하 — 평탄
    NODATA     // 데이터 포인트 부족 — 기울기 계산 불가
}

enum DetectionStatus {
    DETECTED,        // 이상 조합 성립
    NOT_DETECTED,    // 이상 조합 미성립
    UNDETERMINABLE   // 조합 입력 중 NODATA 포함 — 판정 불가
}
```

이상 신호는 위 enum 값들이 특정 조합으로 모일 때 성립합니다.

```
즉각적인 이상 신호 (Collapse)
   ResourceStatus = SPIKED
   ResponseStatus = SLOWED
                      → DetectionStatus = DETECTED


점진적인 이상 신호 (Erosion)
   자원 TrendStatus  = RISING
   응답 TrendStatus  = RISING
                      → DetectionStatus = DETECTED


외부 영향의 이상 신호 (External Impact)
   ResourceStatus = NORMAL
   ResponseStatus = SLOWED   (외부 응답시간만 측정)
                      → DetectionStatus = DETECTED
```

---

**장애 감지의 전체 구조**

```
이상 원인의 위치
├─ 내부
│   ├─ 자원 (cpu, heap) — ResourceStatus
│   │    ├─ 즉각 급등           → SPIKED
│   │    ├─ 즉각 급등 아님       → NORMAL
│   │    └─ 측정 데이터 없음     → NODATA
│   │
│   ├─ 자원 추세 — TrendStatus
│   │    ├─ 점진 상승           → RISING
│   │    ├─ 평탄                → FLAT
│   │    └─ 데이터 포인트 부족   → NODATA
│   │
│   ├─ 응답시간 (전체 Span) — ResponseStatus
│   │    ├─ 즉각 지연           → SLOWED
│   │    ├─ 즉각 지연 아님       → NORMAL
│   │    └─ 측정 데이터 없음     → NODATA
│   │
│   └─ 응답시간 추세 — TrendStatus
│        ├─ 점진 상승           → RISING
│        ├─ 평탄                → FLAT
│        └─ 데이터 포인트 부족   → NODATA
│
└─ 외부
    ├─ 자원 (cpu, memoryRate) — ResourceStatus
    │    ├─ 절대 임계 이하       → NORMAL
    │    ├─ 절대 임계 초과       → SPIKED
    │    └─ 측정 데이터 없음     → NODATA
    │
    └─ 외부 응답시간 (EXTERNAL Span) — ResponseStatus
         ├─ 평소 대비 지연       → SLOWED
         ├─ 평소 대비 평탄       → NORMAL
         └─ 측정 데이터 없음     → NODATA

조합 결과 → DetectionStatus
   각 규칙의 이상 조합 성립          → DETECTED
   각 규칙의 이상 조합 미성립        → NOT_DETECTED
   조합 입력 중 NODATA 하나라도 포함 → UNDETERMINABLE
```

---

<a id="status-formula"></a>
**status 결정 수식**

각 측정 요소의 enum 값은 정해진 수식으로 결정됩니다. 구간 평균으로 노이즈를 제거하고 평소 대비 임계 배수를 넘는지를 보는 식이 기본 구조이며, 점진 상승 판정만 시간 축 기울기를 추가로 봅니다. 수식에 등장하는 측정 구간과 기준 구간의 주기/길이는 [AI 분석 흐름의 구간 변수 단락](데이터-흐름과-코드-경로#interval-vars)에서 다룹니다.

**자원 급등 (`ResourceStatus = SPIKED`, Collapse용)**
```
avg(cpu)  > baselineCpu  × spikeMultiplier
avg(heap) > baselineHeap × spikeMultiplier
```
- `avg(cpu)` — 최근 측정 구간 동안 수집된 CPU 사용률(%)의 평균
- `avg(heap)` — 최근 측정 구간 동안 수집된 heap 사용량(bytes)의 평균
- `baselineCpu`, `baselineHeap` — 직전 기준 구간의 평균값
- `spikeMultiplier` — 평소 대비 몇 배를 넘어야 SPIKED로 보는지의 임계 배수 (threshold_config 설정값, 기본 3.0)

둘 중 하나라도 만족되면 SPIKED.

**내부 응답 지연 (`ResponseStatus = SLOWED`, Collapse용)**
```
avg(spanDuration) > baselineSpan × spikeMultiplier
```
- `avg(spanDuration)` — 최근 측정 구간 동안 수집된 INTERNAL Span의 평균 응답시간(ms)
- `baselineSpan` — 직전 기준 구간의 INTERNAL Span 평균 응답시간(ms)
- `spikeMultiplier` — 자원 급등과 동일한 임계 배수

**외부 응답 지연 (`ResponseStatus = SLOWED`, External Impact용)**
```
avg(externalDuration) > baselineExternal × externalRatioMultiplier
```
- `avg(externalDuration)` — 최근 측정 구간 동안 수집된 EXTERNAL Span의 평균 응답시간(ms)
- `baselineExternal` — 직전 기준 구간의 EXTERNAL Span 평균 응답시간(ms)
- `externalRatioMultiplier` — 외부 호출 지연 판정용 임계 배수 (자원/내부 응답과 다른 별도 설정값)

**자원 정상 (`ResourceStatus = NORMAL`, External Impact용)** — 외부 영향 판정에서 자원 측은 평소 대비가 아니라 절대 임계 이하인지를 봅니다. 외부 의존성에서 오는 영향을 가리는 게 목적이므로 자원이 평소보다 낮든 평소 수준이든 임계 이하면 충분합니다.
```
avg(cpu)        ≤ cpuThreshold
avg(memoryRate) ≤ memoryThreshold
```
- `avg(cpu)` — 위와 동일
- `avg(memoryRate)` — 최근 측정 구간 동안 `heapUsed / heapMax × 100` 의 평균(%) — 절대값이 아닌 사용률
- `cpuThreshold`, `memoryThreshold` — 자원이 정상 범위에 있는지를 가르는 절대 임계값 (threshold_config 설정값)

**점진 상승 (`TrendStatus = RISING`)**
```
slope = SimpleRegression(시계열 데이터 포인트).getSlope()
RISING ⇔ slope > slopeMinPositive
```
- `시계열 데이터 포인트` — 누적 윈도우 동안 일정 주기로 쌓인 측정값들
- `SimpleRegression` — Apache Commons Math 라이브러리의 단순 선형 회귀 클래스
- `slope` — 데이터 포인트들에 직선을 맞췄을 때의 기울기 (Y축 변화량 / X축 변화량). 데이터 포인트가 2개 미만이면 NaN
- `slopeMinPositive` — "양수이면서 의미 있는 상승"이라고 보는 최소 기울기 임계값 (threshold_config 설정값)

코드가 위 수식으로 측정 요소의 status를 결정하고, 그 status 조합으로 DetectionStatus를 확정한 뒤, AI는 그 결과와 근거 데이터를 받아 자연어 원인 설명과 권고를 생성합니다. 감지 결과의 신뢰는 규칙이, 설명의 품질은 AI가 책임집니다.

- [`aipipeline/src/main/java/com/apm/observatory/aipipeline/analysis/evaluator/PerformanceCollapseEvaluator.java`](https://github.com/buss-sooin/apm-observatory/blob/main/aipipeline/src/main/java/com/apm/observatory/aipipeline/analysis/evaluator/PerformanceCollapseEvaluator.java)
- [`aipipeline/src/main/java/com/apm/observatory/aipipeline/analysis/evaluator/PerformanceErosionEvaluator.java`](https://github.com/buss-sooin/apm-observatory/blob/main/aipipeline/src/main/java/com/apm/observatory/aipipeline/analysis/evaluator/PerformanceErosionEvaluator.java)
- [`aipipeline/src/main/java/com/apm/observatory/aipipeline/analysis/evaluator/ExternalImpactEvaluator.java`](https://github.com/buss-sooin/apm-observatory/blob/main/aipipeline/src/main/java/com/apm/observatory/aipipeline/analysis/evaluator/ExternalImpactEvaluator.java)

---

[← 위키 홈](Home)
