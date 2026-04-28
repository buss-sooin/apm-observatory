package com.apm.observatory.aipipeline.analysis.model;

import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.MetricsSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.SpanSnapshot;

import java.time.Instant;
import java.util.List;

public record CollapseIncident(
        String appName,
        Instant analysisStart,
        Instant analysisEnd,
        Instant detectedAt,
        List<MetricsSnapshot> recentMetrics,
        List<SpanSnapshot> recentSpans,
        double baselineCpuAvg,
        double baselineHeapAvg,
        double baselineSpanAvg,
        double avgCpu,
        double avgHeap,
        double avgSpanDuration
) {}