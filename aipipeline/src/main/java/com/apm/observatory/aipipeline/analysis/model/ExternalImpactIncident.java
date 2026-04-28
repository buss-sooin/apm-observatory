package com.apm.observatory.aipipeline.analysis.model;

import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.ExternalSpanSnapshot;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.MetricsSnapshot;

import java.time.Instant;
import java.util.List;

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