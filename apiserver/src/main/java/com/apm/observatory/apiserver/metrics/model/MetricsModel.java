package com.apm.observatory.apiserver.metrics.model;

import java.time.Instant;
import java.util.List;

public class MetricsModel {

    /** GET /metrics/current 응답. */
    public record CurrentMetrics(
            Instant timestamp,
            String appName,
            double cpuUsage,
            long heapUsed,
            long heapMax,
            int threadCount
    ) {}

    /** GET /metrics/trend 응답 원소. */
    public record TrendPoint(
            Instant timestamp,
            double cpuUsage,
            long heapUsed,
            long heapMax
    ) {}

    /**
     * GET /metrics/summary 응답. metrics·threshold_config·erosion_trend_slopes 세 테이블을
     * 조합한다. cpuUsagePercent = avgCpuUsage / cpuThreshold * 100,
     * heapUsagePercent = avgHeapUsagePercent / memoryThreshold * 100으로, 임계값 대비 현재
     * 수준을 나타낸다.
     */
    public record SummaryMetrics(
            double avgCpuUsage,
            double avgHeapUsagePercent,
            double cpuThreshold,
            double memoryThreshold,
            double cpuUsagePercent,
            double heapUsagePercent,
            double resourceSlope,
            double responseSlope
    ) {}

}