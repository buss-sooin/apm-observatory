package com.apm.observatory.aipipeline.analysis.model;

import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.ExternalSpanSnapshot;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.MetricsSnapshot;

import java.time.Instant;
import java.util.List;

/** 외부 영향 사건 — AI 분석 입력(외부 span·baseline·평균). */
public record ExternalImpactIncident(
        String appName,
        Instant analysisStart,
        Instant analysisEnd,
        Instant detectedAt,
        List<MetricsSnapshot> recentMetrics,
        List<ExternalSpanSnapshot> recentExternalSpans,
        double baselineExternalAvg,
        double avgExternalDuration,
        String externalHost
) {}