package com.apm.observatory.aipipeline.calculator;

import com.apm.observatory.aipipeline.analysis.calculator.AppResponseTimeCalculator;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.SpanSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("외부 호출을 뺀, 앱이 책임지는 응답시간의 평균을 trace 단위로 구한다")
class AppResponseTimeCalculatorTest {

    private AppResponseTimeCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new AppResponseTimeCalculator();
    }

    @Test
    @DisplayName("한 trace의 응답시간은 ROOT에서 EXTERNAL 합을 뺀 값이다")
    void 외부_제외_응답시간() {
        // ROOT 1000 - EXTERNAL 300 = 700
        List<SpanSnapshot> spans = List.of(
                span("trace-1", "ROOT", 1000L),
                span("trace-1", "EXTERNAL", 300L)
        );
        assertThat(calculator.calculateAverage(spans)).hasValue(700.0);
    }

    @Test
    @DisplayName("여러 trace는 각 trace의 응답시간을 trace 단위로 평균낸다")
    void 여러_trace_평균() {
        // trace-1: 800 - 200 = 600
        // trace-2: 400 - 0   = 400
        // 평균 (600 + 400) / 2 = 500
        List<SpanSnapshot> spans = List.of(
                span("trace-1", "ROOT", 800L),
                span("trace-1", "EXTERNAL", 200L),
                span("trace-2", "ROOT", 400L)
        );
        assertThat(calculator.calculateAverage(spans)).hasValue(500.0);
    }

    @Test
    @DisplayName("EXTERNAL 합이 ROOT보다 크면 0으로 클램핑한다")
    void 음수는_0으로_클램핑() {
        // trace-1: 300 - 500 = -200 → 0
        // trace-2: 600 - 0   = 600
        // 평균 (0 + 600) / 2 = 300
        List<SpanSnapshot> spans = List.of(
                span("trace-1", "ROOT", 300L),
                span("trace-1", "EXTERNAL", 500L),
                span("trace-2", "ROOT", 600L)
        );
        assertThat(calculator.calculateAverage(spans)).hasValue(300.0);
    }

    @Test
    @DisplayName("ROOT가 없는 trace는 평균에서 제외한다")
    void ROOT_없는_trace_제외() {
        // trace-1: 900 - 300 = 600
        // trace-2: ROOT 없음 → 제외
        // 평균 600
        List<SpanSnapshot> spans = List.of(
                span("trace-1", "ROOT", 900L),
                span("trace-1", "EXTERNAL", 300L),
                span("trace-2", "EXTERNAL", 400L)
        );
        assertThat(calculator.calculateAverage(spans)).hasValue(600.0);
    }

    @Test
    @DisplayName("ROOT가 있는 trace가 하나도 없으면 빈 값")
    void ROOT_없으면_빈값() {
        List<SpanSnapshot> spans = List.of(
                span("trace-1", "EXTERNAL", 400L),
                span("trace-1", "DB", 200L)
        );
        assertThat(calculator.calculateAverage(spans)).isEmpty();
    }

    @Test
    @DisplayName("span이 없으면 빈 값")
    void span_없으면_빈값() {
        assertThat(calculator.calculateAverage(List.of())).isEmpty();
    }

    private SpanSnapshot span(String traceId, String spanType, long durationMs) {
        return new SpanSnapshot(
                "span-" + traceId + "-" + spanType, traceId, "test-app", spanType, durationMs, Instant.now());
    }
}
