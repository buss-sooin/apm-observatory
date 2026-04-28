package com.apm.observatory.aipipeline.performance.port;

import java.time.Instant;
import java.util.List;

public interface ExternalImpactDataPort {

    record MetricsSnapshot(
            Instant timestamp,
            String appName,
            Double cpuUsage,
            Long heapUsed,
            Long heapMax,
            Long diskReadBytes,
            Long diskWriteBytes
    ) {}

    record ExternalSpanSnapshot(
            String spanId,
            String appName,
            String externalHost,
            Long durationMs,
            Instant startTime
    ) {}

    // 최근 Metrics 조회 (자원 정상 여부 확인용)
    List<MetricsSnapshot> getRecentMetrics(String appName, Instant start, Instant end);

    // 최근 EXTERNAL Span 조회
    List<ExternalSpanSnapshot> getRecentExternalSpans(String appName, Instant start, Instant end);

    // 평소 기준 EXTERNAL Span 평균 응답시간
    Double getBaselineExternalSpanAvg(String appName, Instant start, Instant end);

}