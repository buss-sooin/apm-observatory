package com.apm.observatory.apiserver.config.model;

import java.time.LocalTime;

public class ConfigModel {

    // POST /config/threshold 요청
    public record ThresholdRequest(
            String appName,
            Double cpuThreshold,
            Double memoryThreshold,
            Long diskIoThreshold,
            Double spanDurationMultiplier,
            Double externalRatioMultiplier,
            Double slopeMinPositive
    ) {}

    // POST /config/threshold 응답
    public record ThresholdResponse(
            String appName,
            double cpuThreshold,
            double memoryThreshold,
            long diskIoThreshold,
            double spanDurationMultiplier,
            double externalRatioMultiplier,
            double slopeMinPositive
    ) {}

    // POST /config/business-cycle 요청
    // 의도: appName 필수, 나머지 null이면 기존값 유지 (upsert)
    // 시간 형식: HH:mm (예: "09:00", "18:00")
    public record BusinessCycleRequest(
            String appName,
            LocalTime cycleStart,
            LocalTime cycleEnd,
            LocalTime peakStart,
            LocalTime peakEnd
    ) {}

    // POST /config/business-cycle 응답
    public record BusinessCycleResponse(
            String appName,
            LocalTime cycleStart,
            LocalTime cycleEnd,
            LocalTime peakStart,
            LocalTime peakEnd
    ) {}

}