package com.apm.observatory.aipipeline.analysis.model;

import java.time.Instant;

public record ErosionDataPoint(
        Instant timestamp,
        double avgCpu,
        double avgHeap,
        double avgSpanDuration
) {}