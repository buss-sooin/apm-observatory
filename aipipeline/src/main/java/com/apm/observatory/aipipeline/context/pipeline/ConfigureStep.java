package com.apm.observatory.aipipeline.context.pipeline;

import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;
import com.apm.observatory.aipipeline.performance.model.PerformanceTrend;
import com.apm.observatory.aipipeline.threshold.model.ThresholdConfig;

public class ConfigureStep {

    private final AnalysisDependencies dependencies;
    private final String appName;
    private final PerformanceTrend trend;

    ConfigureStep(AnalysisDependencies dependencies,
                  String appName,
                  PerformanceTrend trend) {
        this.dependencies = dependencies;
        this.appName = appName;
        this.trend = trend;
    }

    public BaselineStep configure(ThresholdConfig threshold) {
        return new BaselineStep(dependencies, appName, trend, threshold);
    }

}