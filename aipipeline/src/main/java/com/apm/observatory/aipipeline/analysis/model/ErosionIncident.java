package com.apm.observatory.aipipeline.analysis.model;

import java.time.Instant;
import java.util.List;

/** 성능 침식 사건 — AI 분석 입력(추세 포인트·기울기). */
public record ErosionIncident(
        String appName,
        Instant analysisStart,
        Instant analysisEnd,
        Instant detectedAt,
        List<ErosionDataPoint> trendPoints,
        double resourceSlope,
        double responseSlope
) {}