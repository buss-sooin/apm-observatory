package com.apm.observatory.aipipeline.performance.port;

import java.time.Instant;
import java.util.List;

public interface PerformanceDataPort {

    // 자원 스냅샷 (Evaluator가 판단에 쓰는 순수 데이터)
    record MetricsSnapshot(
            Instant timestamp,
            String appName,
            Double cpuUsage,
            Long heapUsed,
            Long heapMax,
            Long diskReadBytes,
            Long diskWriteBytes
    ) {}

    // Span 스냅샷
    record SpanSnapshot(
            String spanId,
            String appName,
            String spanType,
            Long durationMs,
            Instant startTime
    ) {}

    // 최근 Metrics 조회
    List<MetricsSnapshot> getRecentMetrics(String appName, Instant start, Instant end);

    // 평소 기준 CPU 평균
    Double getBaselineCpuAvg(String appName, Instant start, Instant end);

    // 평소 기준 메모리 평균
    Double getBaselineHeapAvg(String appName, Instant start, Instant end);

    // 최근 Span 조회
    List<SpanSnapshot> getRecentSpans(String appName, Instant start, Instant end);

    // 평소 기준 Span 평균 응답시간
    Double getBaselineSpanAvg(String appName, String spanType, Instant start, Instant end);

}