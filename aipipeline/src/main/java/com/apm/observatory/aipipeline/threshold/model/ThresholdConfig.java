package com.apm.observatory.aipipeline.threshold.model;

/** 앱별 이상 판정 임계값 묶음. */
public record ThresholdConfig(
        String appName,
        double cpuThreshold,
        double memoryThreshold,
        double spanDurationMultiplier,
        double externalRatioMultiplier,
        double slopeMinPositive
) {}