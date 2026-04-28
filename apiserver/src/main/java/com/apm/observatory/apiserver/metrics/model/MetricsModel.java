package com.apm.observatory.apiserver.metrics.model;

import java.time.Instant;
import java.util.List;

public class MetricsModel {

    // GET /metrics/current 응답
    public record CurrentMetrics(
            Instant timestamp,
            String appName,
            double cpuUsage,
            long heapUsed,
            long heapMax,
            int threadCount
    ) {}

    // GET /metrics/trend 응답 원소
    public record TrendPoint(
            Instant timestamp,
            double cpuUsage,
            long heapUsed,
            long heapMax
    ) {}

    // GET /metrics/summary 응답
    // 의도: 세 테이블(metrics, threshold_config, erosion_trend_slopes) 조합
    // cpuUsagePercent = avgCpuUsage / cpuThreshold * 100 → 임계값 대비 현재 수준
    // heapUsagePercent = avgHeapUsagePercent / memoryThreshold * 100
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