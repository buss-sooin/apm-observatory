package com.apm.observatory.aipipeline.evaluator;

import com.apm.observatory.aipipeline.analysis.evaluator.PerformanceErosionEvaluator;
import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.analysis.status.TrendStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("자원과 응답시간이 완만히 동반 상승하는 추세일 때만 PerformanceErosion으로 판정한다")
class PerformanceErosionEvaluatorTest {

    private PerformanceErosionEvaluator evaluator;
    private static final double SLOPE_MIN_POSITIVE = 0.01;

    @BeforeEach
    void setUp() {
        evaluator = new PerformanceErosionEvaluator();
    }

    // ── calculateSlope ────────────────────────────────────────────

    @Test
    @DisplayName("우상향 데이터의 기울기는 양수다")
    void 우상향_기울기_양수() {
        List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        assertThat(evaluator.calculateSlope(values)).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("우하향 데이터의 기울기는 음수다")
    void 우하향_기울기_음수() {
        List<Double> values = List.of(5.0, 4.0, 3.0, 2.0, 1.0);
        assertThat(evaluator.calculateSlope(values)).isLessThan(0.0);
    }

    @Test
    @DisplayName("수평 데이터의 기울기는 0에 가깝다")
    void 수평_기울기_0() {
        List<Double> values = List.of(3.0, 3.0, 3.0, 3.0, 3.0);
        assertThat(evaluator.calculateSlope(values)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.001));
    }

    // ── toTrendStatus ─────────────────────────────────────────────

    @Test
    @DisplayName("기울기가 최소 양수 기준 초과면 RISING")
    void 기울기_양수면_RISING() {
        assertThat(evaluator.toTrendStatus(0.05, SLOPE_MIN_POSITIVE))
                .isEqualTo(TrendStatus.RISING);
    }

    @Test
    @DisplayName("기울기가 최소 양수 기준 이하면 FLAT")
    void 기울기_0이면_FLAT() {
        assertThat(evaluator.toTrendStatus(0.0, SLOPE_MIN_POSITIVE))
                .isEqualTo(TrendStatus.FLAT);
    }

    // ── evaluate ─────────────────────────────────────────────────

    @Test
    @DisplayName("자원 RISING AND 응답시간 RISING이면 DETECTED")
    void RISING_AND_RISING_이면_DETECTED() {
        assertThat(evaluator.evaluate(TrendStatus.RISING, TrendStatus.RISING))
                .isEqualTo(DetectionStatus.DETECTED);
    }

    @Test
    @DisplayName("자원 FLAT이면 NOT_DETECTED")
    void 자원_FLAT_이면_NOT_DETECTED() {
        assertThat(evaluator.evaluate(TrendStatus.FLAT, TrendStatus.RISING))
                .isEqualTo(DetectionStatus.NOT_DETECTED);
    }

    @Test
    @DisplayName("응답시간 FLAT이면 NOT_DETECTED")
    void 응답시간_FLAT_이면_NOT_DETECTED() {
        assertThat(evaluator.evaluate(TrendStatus.RISING, TrendStatus.FLAT))
                .isEqualTo(DetectionStatus.NOT_DETECTED);
    }

}