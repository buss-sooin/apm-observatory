package com.apm.observatory.apiserver.config.model;

import java.time.LocalTime;

public class ConfigModel {

    /** POST /config/threshold 요청. */
    public record ThresholdRequest(
            String appName,
            Double cpuThreshold,
            Double memoryThreshold,
            Long diskIoThreshold,
            Double spanDurationMultiplier,
            Double externalRatioMultiplier,
            Double slopeMinPositive
    ) {}

    /** POST /config/threshold 응답. */
    public record ThresholdResponse(
            String appName,
            double cpuThreshold,
            double memoryThreshold,
            long diskIoThreshold,
            double spanDurationMultiplier,
            double externalRatioMultiplier,
            double slopeMinPositive
    ) {}

    /**
     * POST /config/business-cycle 요청. appName은 필수이고 나머지는 null이면 기존값을 유지한다
     * (upsert). 시간은 HH:mm 형식이다(예: "09:00", "18:00").
     */
    public record BusinessCycleRequest(
            String appName,
            LocalTime cycleStart,
            LocalTime cycleEnd,
            LocalTime peakStart,
            LocalTime peakEnd
    ) {}

    /** POST /config/business-cycle 응답. */
    public record BusinessCycleResponse(
            String appName,
            LocalTime cycleStart,
            LocalTime cycleEnd,
            LocalTime peakStart,
            LocalTime peakEnd
    ) {}

}