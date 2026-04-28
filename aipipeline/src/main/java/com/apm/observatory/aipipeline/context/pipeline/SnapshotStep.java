package com.apm.observatory.aipipeline.context.pipeline;

import com.apm.observatory.aipipeline.context.loader.SnapshotLoader;
import com.apm.observatory.aipipeline.context.model.AnalysisContext;
import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;
import com.apm.observatory.aipipeline.context.model.BaselineMetrics;
import com.apm.observatory.aipipeline.performance.model.PerformanceSnapshot;
import com.apm.observatory.aipipeline.performance.model.PerformanceTrend;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort;
import com.apm.observatory.aipipeline.threshold.model.ThresholdConfig;

import java.time.Instant;

public class SnapshotStep {

    private final AnalysisDependencies dependencies;
    private final String appName;
    private final PerformanceTrend trend;
    private final ThresholdConfig threshold;
    private final BaselineMetrics baseline;

    SnapshotStep(AnalysisDependencies dependencies,
                 String appName,
                 PerformanceTrend trend,
                 ThresholdConfig threshold,
                 BaselineMetrics baseline) {
        this.dependencies = dependencies;
        this.appName = appName;
        this.trend = trend;
        this.threshold = threshold;
        this.baseline = baseline;
    }

    // Functional: 람다가 로딩 방식 결정, Step은 실행만 담당
    public AnalyzeStep loadSnapshot(SnapshotLoader loader,
                                    Instant start,
                                    Instant end,
                                    PerformanceDataPort port,
                                    ExternalImpactDataPort extPort) {
        PerformanceSnapshot snapshot = loader.load(port, extPort, start, end, baseline);

        AnalysisContext context = AnalysisContext.builder()
                .appName(appName)
                .trend(trend)
                .threshold(threshold)
                .baseline(baseline)
                .build()
                .withSnapshot(snapshot);

        return new AnalyzeStep(dependencies, context);
    }

}