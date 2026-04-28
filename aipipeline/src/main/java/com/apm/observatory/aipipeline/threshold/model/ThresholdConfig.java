package com.apm.observatory.aipipeline.threshold.model;

public record ThresholdConfig(
        String appName,
        double cpuThreshold,
        double memoryThreshold,
        double spanDurationMultiplier,
        double externalRatioMultiplier,
        double slopeMinPositive
) {}