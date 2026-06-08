package com.apm.observatory.aipipeline.performance.port;

import java.time.Instant;
import java.util.List;

/**
 * 외부 연동(EXTERNAL Span) 영향 분석에 쓰는 데이터 공급 계약. 자원은
 * 정상인데 외부 구간만 느린지 보기 위해 metrics와 EXTERNAL span을 함께
 * 조회한다.
 */
public interface ExternalImpactDataPort {

    /** 자원 스냅샷. */
    record MetricsSnapshot(
            Instant timestamp,
            String appName,
            Double cpuUsage,
            Long heapUsed,
            Long heapMax,
            Long diskReadBytes,
            Long diskWriteBytes
    ) {}

    /** EXTERNAL Span 스냅샷. */
    record ExternalSpanSnapshot(
            String spanId,
            String appName,
            String externalHost,
            Long durationMs,
            Instant startTime
    ) {}

    List<MetricsSnapshot> getRecentMetrics(String appName, Instant start, Instant end);

    List<ExternalSpanSnapshot> getRecentExternalSpans(String appName, Instant start, Instant end);

    Double getBaselineExternalSpanAvg(String appName, Instant start, Instant end);

}
