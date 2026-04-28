package com.apm.observatory.aipipeline.evaluator;

import com.apm.observatory.aipipeline.analysis.evaluator.PerformanceCollapseEvaluator;
import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.analysis.status.ResourceStatus;
import com.apm.observatory.aipipeline.analysis.status.ResponseStatus;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.MetricsSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.SpanSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("자원 급등과 응답 지연이 동시 발생했을 때만 PerformanceCollapse로 판정한다")
class PerformanceCollapseEvaluatorTest {

    private PerformanceCollapseEvaluator evaluator;
    private static final double SPIKE_MULTIPLIER = 3.0;

    @BeforeEach
    void setUp() {
        evaluator = new PerformanceCollapseEvaluator();
    }

    // ── isResourceSpiked ──────────────────────────────────────────

    @Test
    @DisplayName("CPU가 평소 대비 3배 초과하면 SPIKED")
    void cpu_급등시_SPIKED() {
        List<MetricsSnapshot> metrics = List.of(
                metricsSnapshot(60.0, 1000L, 8000L)
        );
        // 평소 CPU 15% × 3.0 = 45% → 현재 평균 60% > 45% → SPIKED
        assertThat(evaluator.isResourceSpiked(metrics, 15.0, 500.0, SPIKE_MULTIPLIER))
                .isEqualTo(ResourceStatus.SPIKED);
    }

    @Test
    @DisplayName("Memory가 평소 대비 3배 초과하면 SPIKED")
    void memory_급등시_SPIKED() {
        List<MetricsSnapshot> metrics = List.of(
                metricsSnapshot(10.0, 6000L, 8000L)
        );
        // 평소 Heap 1000 × 3.0 = 3000 → 현재 평균 6000 > 3000 → SPIKED
        assertThat(evaluator.isResourceSpiked(metrics, 5.0, 1000.0, SPIKE_MULTIPLIER))
                .isEqualTo(ResourceStatus.SPIKED);
    }

    @Test
    @DisplayName("CPU와 Memory 모두 정상이면 NORMAL")
    void cpu_memory_정상시_NORMAL() {
        List<MetricsSnapshot> metrics = List.of(
                metricsSnapshot(20.0, 1000L, 8000L)
        );
        // 평소 CPU 15% × 3.0 = 45% → 현재 20% < 45% → NORMAL
        // 평소 Heap 500 × 3.0 = 1500 → 현재 1000 < 1500 → NORMAL
        assertThat(evaluator.isResourceSpiked(metrics, 15.0, 500.0, SPIKE_MULTIPLIER))
                .isEqualTo(ResourceStatus.NORMAL);
    }

    // ── isSpanSlowed ──────────────────────────────────────────────

    @Test
    @DisplayName("Span 평균 응답시간이 평소 대비 3배 초과하면 SLOWED")
    void span_응답지연시_SLOWED() {
        List<SpanSnapshot> spans = List.of(
                spanSnapshot(900L),
                spanSnapshot(1200L)
        );
        // 평균 1050ms, 평소 300ms × 3.0 = 900ms → 1050 > 900 → SLOWED
        assertThat(evaluator.isSpanSlowed(spans, 300.0, SPIKE_MULTIPLIER))
                .isEqualTo(ResponseStatus.SLOWED);
    }

    @Test
    @DisplayName("Span 평균 응답시간이 정상이면 NORMAL")
    void span_정상시_NORMAL() {
        List<SpanSnapshot> spans = List.of(
                spanSnapshot(400L),
                spanSnapshot(500L)
        );
        // 평균 450ms, 평소 300ms × 3.0 = 900ms → 450 < 900 → NORMAL
        assertThat(evaluator.isSpanSlowed(spans, 300.0, SPIKE_MULTIPLIER))
                .isEqualTo(ResponseStatus.NORMAL);
    }

    // ── evaluate ─────────────────────────────────────────────────

    @Test
    @DisplayName("SPIKED AND SLOWED 이면 DETECTED")
    void SPIKED_AND_SLOWED_이면_DETECTED() {
        assertThat(evaluator.evaluate(ResourceStatus.SPIKED, ResponseStatus.SLOWED))
                .isEqualTo(DetectionStatus.DETECTED);
    }

    @Test
    @DisplayName("SPIKED AND NORMAL 이면 NOT_DETECTED")
    void SPIKED_AND_NORMAL_이면_NOT_DETECTED() {
        assertThat(evaluator.evaluate(ResourceStatus.SPIKED, ResponseStatus.NORMAL))
                .isEqualTo(DetectionStatus.NOT_DETECTED);
    }

    @Test
    @DisplayName("NORMAL AND SLOWED 이면 NOT_DETECTED")
    void NORMAL_AND_SLOWED_이면_NOT_DETECTED() {
        assertThat(evaluator.evaluate(ResourceStatus.NORMAL, ResponseStatus.SLOWED))
                .isEqualTo(DetectionStatus.NOT_DETECTED);
    }

    @Test
    @DisplayName("ResourceStatus NODATA면 UNDETERMINABLE")
    void ResourceStatus_NODATA_이면_UNDETERMINABLE() {
        assertThat(evaluator.evaluate(ResourceStatus.NODATA, ResponseStatus.SLOWED))
                .isEqualTo(DetectionStatus.UNDETERMINABLE);
    }

    @Test
    @DisplayName("ResponseStatus NODATA면 UNDETERMINABLE")
    void ResponseStatus_NODATA_이면_UNDETERMINABLE() {
        assertThat(evaluator.evaluate(ResourceStatus.SPIKED, ResponseStatus.NODATA))
                .isEqualTo(DetectionStatus.UNDETERMINABLE);
    }

    // ── 헬퍼 메서드 ───────────────────────────────────────────────

    private MetricsSnapshot metricsSnapshot(double cpuUsage, long heapUsed, long heapMax) {
        return new MetricsSnapshot(
                Instant.now(), "test-app", cpuUsage, heapUsed, heapMax, 0L, 0L);
    }

    private SpanSnapshot spanSnapshot(long durationMs) {
        return new SpanSnapshot(
                "span-id", "test-app", "INTERNAL", durationMs, Instant.now());
    }

}