package com.apm.observatory.aipipeline.analysis.model;

import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.MetricsSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.SpanSnapshot;

import java.time.Instant;
import java.util.List;

/** 성능 붕괴 사건 — AI 분석 입력(구간·baseline·최근 평균·원시 스냅샷). */
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