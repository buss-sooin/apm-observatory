package com.apm.observatory.aipipeline.context.pipeline;

import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.context.model.AnalysisContext;
import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AnalyzeStep {

    private final AnalysisDependencies dependencies;
    private final AnalysisContext context;

    AnalyzeStep(AnalysisDependencies dependencies, AnalysisContext context) {
        this.dependencies = dependencies;
        this.context = context;
    }

    public TransferStep analyzeAnomalies() {
        dependencies.detectionStrategies().forEach(strategy -> {
            DetectionStatus status = strategy.detectAnomaly(context, dependencies);
            if (status == DetectionStatus.DETECTED) {
                strategy.onAnomalyDetected(context, dependencies);
            }
        });
        return new TransferStep(dependencies, context);
    }

}