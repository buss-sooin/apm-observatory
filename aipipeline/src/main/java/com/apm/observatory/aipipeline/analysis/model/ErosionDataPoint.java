package com.apm.observatory.aipipeline.analysis.model;

import java.time.Instant;

/** erosion 추세의 한 시점 평균값(cpu·heap·span). */
public record ErosionDataPoint(
        Instant timestamp,
        double avgCpu,
        double avgHeap,
        double avgSpanDuration
) {}