package com.apm.observatory.aipipeline.analysis.model;

import java.time.Instant;
import java.util.List;

public record ErosionIncident(
        String appName,
        Instant analysisStart,
        Instant analysisEnd,
        Instant detectedAt,
        List<ErosionDataPoint> trendPoints,
        double resourceSlope,
        double responseSlope
) {}