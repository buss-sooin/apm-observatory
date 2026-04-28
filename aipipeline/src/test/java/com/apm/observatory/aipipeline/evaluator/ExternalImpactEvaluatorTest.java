package com.apm.observatory.aipipeline.evaluator;

import com.apm.observatory.aipipeline.analysis.evaluator.ExternalImpactEvaluator;
import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.analysis.status.ResourceStatus;
import com.apm.observatory.aipipeline.analysis.status.ResponseStatus;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.MetricsSnapshot;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.ExternalSpanSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("자원은 정상이고 외부 API만 급등했을 때만 ExternalImpact로 판정한다")
class ExternalImpactEvaluatorTest {

    private ExternalImpactEvaluator evaluator;
    private static final double CPU_THRESHOLD = 80.0;
    private static final double MEMORY_THRESHOLD = 80.0;
    private static final double EXTERNAL_RATIO_MULTIPLIER = 3.0;

    @BeforeEach
    void setUp() {
        evaluator = new ExternalImpactEvaluator();
    }

    // ── checkResourceStatus ───────────────────────────────────────

    @Test
    @DisplayName("CPU와 Memory 모두 임계값 이하면 NORMAL")
    void 자원_정상이면_NORMAL() {
        List<MetricsSnapshot> metrics = List.of(
                metricsSnapshot(50.0, 4000L, 8000L)  // CPU 50%, Heap 50%
        );
        // CPU 50% < 80%, Heap 4000/8000 = 50% < 80% → NORMAL
        assertThat(evaluator.checkResourceStatus(metrics, CPU_THRESHOLD, MEMORY_THRESHOLD))
                .isEqualTo(ResourceStatus.NORMAL);
    }

    @Test
    @DisplayName("CPU가 임계값 초과하면 SPIKED")
    void CPU_초과하면_SPIKED() {
        List<MetricsSnapshot> metrics = List.of(
                metricsSnapshot(90.0, 4000L, 8000L)  // CPU 90%
        );
        // CPU 90% > 80% → SPIKED
        assertThat(evaluator.checkResourceStatus(metrics, CPU_THRESHOLD, MEMORY_THRESHOLD))
                .isEqualTo(ResourceStatus.SPIKED);
    }

    @Test
    @DisplayName("Memory 사용률이 임계값 초과하면 SPIKED")
    void Memory_초과하면_SPIKED() {
        List<MetricsSnapshot> metrics = List.of(
                metricsSnapshot(50.0, 7000L, 8000L)  // Heap 7000/8000 = 87.5%
        );
        // Heap 87.5% > 80% → SPIKED
        assertThat(evaluator.checkResourceStatus(metrics, CPU_THRESHOLD, MEMORY_THRESHOLD))
                .isEqualTo(ResourceStatus.SPIKED);
    }

    @Test
    @DisplayName("빈 리스트면 NODATA")
    void 빈_리스트면_NODATA() {
        assertThat(evaluator.checkResourceStatus(List.of(), CPU_THRESHOLD, MEMORY_THRESHOLD))
                .isEqualTo(ResourceStatus.NODATA);
    }

// ── checkExternalSpanStatus ───────────────────────────────────

    @Test
    @DisplayName("외부 Span 평균이 평소 대비 3배 초과하면 SLOWED")
    void 외부Span_급등시_SLOWED() {
        List<ExternalSpanSnapshot> spans = List.of(
                externalSpanSnapshot(1200L),
                externalSpanSnapshot(900L)
        );
        // 평균 1050ms, 평소 300ms × 3.0 = 900ms → 1050 > 900 → SLOWED
        assertThat(evaluator.checkExternalSpanStatus(spans, 300.0, EXTERNAL_RATIO_MULTIPLIER))
                .isEqualTo(ResponseStatus.SLOWED);
    }

    @Test
    @DisplayName("외부 Span 평균이 정상이면 NORMAL")
    void 외부Span_정상시_NORMAL() {
        List<ExternalSpanSnapshot> spans = List.of(
                externalSpanSnapshot(400L),
                externalSpanSnapshot(500L)
        );
        // 평균 450ms, 평소 300ms × 3.0 = 900ms → 450 < 900 → NORMAL
        assertThat(evaluator.checkExternalSpanStatus(spans, 300.0, EXTERNAL_RATIO_MULTIPLIER))
                .isEqualTo(ResponseStatus.NORMAL);
    }

    @Test
    @DisplayName("빈 리스트면 NODATA")
    void 외부Span_빈리스트면_NODATA() {
        assertThat(evaluator.checkExternalSpanStatus(List.of(), 300.0, EXTERNAL_RATIO_MULTIPLIER))
                .isEqualTo(ResponseStatus.NODATA);
    }

// ── evaluate ─────────────────────────────────────────────────

    @Test
    @DisplayName("자원 NORMAL AND 외부Span SLOWED면 DETECTED")
    void NORMAL_AND_SLOWED_이면_DETECTED() {
        assertThat(evaluator.evaluate(ResourceStatus.NORMAL, ResponseStatus.SLOWED))
                .isEqualTo(DetectionStatus.DETECTED);
    }

    @Test
    @DisplayName("자원 SPIKED면 NOT_DETECTED")
    void 자원_SPIKED_이면_NOT_DETECTED() {
        assertThat(evaluator.evaluate(ResourceStatus.SPIKED, ResponseStatus.SLOWED))
                .isEqualTo(DetectionStatus.NOT_DETECTED);
    }

    @Test
    @DisplayName("NODATA 포함이면 UNDETERMINABLE")
    void NODATA_포함이면_UNDETERMINABLE() {
        assertThat(evaluator.evaluate(ResourceStatus.NODATA, ResponseStatus.SLOWED))
                .isEqualTo(DetectionStatus.UNDETERMINABLE);
    }

    // ── 헬퍼 메서드 ───────────────────────────────────────────────

    private MetricsSnapshot metricsSnapshot(double cpuUsage, long heapUsed, long heapMax) {
        return new MetricsSnapshot(
                Instant.now(), "test-app", cpuUsage, heapUsed, heapMax, 0L, 0L);
    }

    private ExternalSpanSnapshot externalSpanSnapshot(long durationMs) {
        return new ExternalSpanSnapshot(
                "span-id", "test-app", "external-host", durationMs, Instant.now());
    }

}