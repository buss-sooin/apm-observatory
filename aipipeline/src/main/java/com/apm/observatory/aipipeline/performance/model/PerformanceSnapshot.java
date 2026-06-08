package com.apm.observatory.aipipeline.performance.model;

import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.ExternalSpanSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.MetricsSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.SpanSnapshot;

import java.time.Instant;
import java.util.List;

/** 최근 구간 데이터(metrics·span·external)와 baseline 평균을 묶은 분석 입력 스냅샷. */
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