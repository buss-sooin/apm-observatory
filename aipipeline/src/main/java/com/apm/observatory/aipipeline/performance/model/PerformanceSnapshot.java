package com.apm.observatory.aipipeline.performance.model;

import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.ExternalSpanSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.MetricsSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.SpanSnapshot;

import java.time.Instant;
import java.util.List;

public record PerformanceSnapshot(
        String appName,
        Instant startTime,
        Instant endTime,
        List<MetricsSnapshot> recentMetrics,
        List<SpanSnapshot> recentSpans,
        List<ExternalSpanSnapshot> recentExternalSpans,
        double baselineCpuAvg,
        double baselineHeapAvg,
        double baselineSpanAvg,
        double baselineExternalAvg
) {}