package com.apm.observatory.aipipeline.performance.port;

import java.time.Instant;
import java.util.List;

/**
 * 성능 분석에 쓰는 자원·Span 데이터 공급 계약.
 * 조회 결과는 평가기가 판단에 바로 쓰는 순수 스냅샷이다.
 */
public interface PerformanceDataPort {

    /** 자원 스냅샷(cpu·heap·disk). */
    record MetricsSnapshot(
            Instant timestamp,
            String appName,
            Double cpuUsage,
            Long heapUsed,
            Long heapMax,
            Long diskReadBytes,
            Long diskWriteBytes
    ) {}

    /** Span 스냅샷. */
    record SpanSnapshot(
            String spanId,
            String traceId,
            String appName,
            String spanType,
            Long durationMs,
            Instant startTime
    ) {}

    List<MetricsSnapshot> getRecentMetrics(String appName, Instant start, Instant end);

    Double getBaselineCpuAvg(String appName, Instant start, Instant end);

    Double getBaselineHeapAvg(String appName, Instant start, Instant end);

    List<SpanSnapshot> getRecentSpans(String appName, Instant start, Instant end);

}
