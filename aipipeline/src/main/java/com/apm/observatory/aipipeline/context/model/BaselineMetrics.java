package com.apm.observatory.aipipeline.context.model;

/** baseline 비교 기준값 묶음(cpu·heap·span·external 평균). */
public record BaselineMetrics(
        double baselineCpuAvg,
        double baselineHeapAvg,
        double baselineSpanAvg,
        double baselineExternalAvg
) {}