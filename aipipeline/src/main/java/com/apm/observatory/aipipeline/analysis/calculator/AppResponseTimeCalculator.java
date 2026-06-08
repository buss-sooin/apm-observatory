package com.apm.observatory.aipipeline.analysis.calculator;

import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.SpanSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.stream.Collectors;

/**
 * 앱이 책임지는 응답시간을 계산한다. 한 요청(trace)의 전체 시간에서 외부 호출 시간을
 * 뺀 값이며, 외부 의존성 지연은 ExternalImpact 축이 따로 판정하므로 이 계산기는 외부를
 * 제외한 시간만 다룬다. 측정 구간과 기준 구간이 같은 규칙으로 평균을 구하도록 계산을
 * 이 한 곳에서 책임진다.
 */
@Slf4j
@Component
public class AppResponseTimeCalculator {

    /**
     * 앱이 책임지는 응답시간(외부 호출 제외)의 평균을 구한다.
     *
     * <p>span을 trace_id로 묶어 trace마다 응답시간을 구하고, 계산할 수 없는
     * trace(ROOT 없음)는 평균에서 제외한다.
     *
     * @param spans 한 시간 구간에서 수집한 여러 trace의 span 목록(타입 혼재)
     * @return 응답시간을 구할 수 있는 trace들의 평균(ms). 그런 trace가 없으면 빈 값
     */
    public OptionalDouble calculateAverage(List<SpanSnapshot> spans) {
        Map<String, List<SpanSnapshot>> spansByTrace = spans.stream()
                .collect(Collectors.groupingBy(SpanSnapshot::traceId));

        return spansByTrace.entrySet().stream()
                .map(entry -> calculateResponseTimeExcludingExternal(entry.getKey(), entry.getValue()))
                .filter(OptionalLong::isPresent)
                .mapToLong(OptionalLong::getAsLong)
                .average();
    }

    /**
     * 한 trace에서 외부 호출 시간을 뺀 응답시간을 구한다.
     *
     * <p>ROOT가 없으면 응답시간을 정의할 수 없어 빈 값을 돌려준다. EXTERNAL 합이 ROOT보다
     * 커서 음수가 나오면 평탄화된 중첩 호출의 측정 한계로 보고 0으로 클램핑하며, trace_id와
     * 값을 warn 로깅한다.
     *
     * @param traceId    로깅용 trace 식별자
     * @param traceSpans 한 trace에 속한 span 목록
     * @return 외부 제외 응답시간(ms). ROOT가 없으면 빈 값
     */
    private OptionalLong calculateResponseTimeExcludingExternal(String traceId, List<SpanSnapshot> traceSpans) {
        Optional<SpanSnapshot> root = traceSpans.stream()
                .filter(s -> "ROOT".equals(s.spanType()))
                .findFirst();
        if (root.isEmpty()) {
            return OptionalLong.empty();
        }

        long externalDurationSum = traceSpans.stream()
                .filter(s -> "EXTERNAL".equals(s.spanType()))
                .mapToLong(SpanSnapshot::durationMs)
                .sum();

        long responseTime = root.get().durationMs() - externalDurationSum;
        if (responseTime < 0L) {
            log.warn("앱 응답시간 음수 감지: trace_id={}, root={}ms, external합={}ms"
                            + " — 평탄화된 중첩 호출 가능성. 0으로 클램핑.",
                    traceId, root.get().durationMs(), externalDurationSum);
        }
        return OptionalLong.of(Math.max(0L, responseTime));
    }
}
