package com.apm.observatory.aipipeline.context.model;

public record BaselineMetrics(
        double baselineCpuAvg,
        double baselineHeapAvg,
        double baselineSpanAvg,
        double baselineExternalAvg
) {}