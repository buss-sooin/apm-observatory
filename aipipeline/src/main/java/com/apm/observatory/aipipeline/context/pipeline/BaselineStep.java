package com.apm.observatory.aipipeline.context.pipeline;

import com.apm.observatory.aipipeline.context.loader.BaselineLoader;
import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;
import com.apm.observatory.aipipeline.context.model.BaselineMetrics;
import com.apm.observatory.aipipeline.performance.model.PerformanceTrend;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort;
import com.apm.observatory.aipipeline.threshold.model.ThresholdConfig;

import java.time.Instant;

public class BaselineStep {

    private final AnalysisDependencies dependencies;
    private final String appName;
    private final PerformanceTrend trend;
    private final ThresholdConfig threshold;

    BaselineStep(AnalysisDependencies dependencies,
                 String appName,
                 PerformanceTrend trend,
                 ThresholdConfig threshold) {
        this.dependencies = dependencies;
        this.appName = appName;
        this.trend = trend;
        this.threshold = threshold;
    }

    // Functional: 람다가 로딩 방식 결정, Step은 실행만 담당
    public SnapshotStep loadBaseline(BaselineLoader loader,
                                     Instant start,
                                     Instant end,
                                     PerformanceDataPort port,
                                     ExternalImpactDataPort extPort) {
        BaselineMetrics baseline = loader.load(port, extPort, start, end);
        return new SnapshotStep(dependencies, appName, trend, threshold, baseline);
    }

}