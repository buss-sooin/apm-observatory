package com.apm.observatory.aipipeline.context.pipeline;

import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;
import com.apm.observatory.aipipeline.performance.model.PerformanceTrend;

public class PerformanceAnalysisPipelineContext {

    public static ConfigureStep startWith(
            AnalysisDependencies dependencies,
            String appName,
            PerformanceTrend trend) {
        return new ConfigureStep(dependencies, appName, trend);
    }

}