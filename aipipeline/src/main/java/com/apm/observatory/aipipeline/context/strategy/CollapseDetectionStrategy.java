package com.apm.observatory.aipipeline.context.strategy;


import com.apm.observatory.aipipeline.analysis.model.CollapseIncident;
import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.analysis.status.ResourceStatus;
import com.apm.observatory.aipipeline.analysis.status.ResponseStatus;
import com.apm.observatory.aipipeline.context.model.AnalysisContext;
import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.MetricsSnapshot;
import com.apm.observatory.aipipeline.ai.model.AiCallResult;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
public class CollapseDetectionStrategy implements AnomalyDetectionStrategy {

    @Override
    public DetectionStatus detectAnomaly(AnalysisContext context, AnalysisDependencies dependencies) {
        ResourceStatus resourceStatus = dependencies.collapseEvaluator().isResourceSpiked(
                context.recentMetrics(), context.baselineCpuAvg(),
                context.baselineHeapAvg(), context.spikeMultiplier());
        ResponseStatus responseStatus = dependencies.collapseEvaluator().isSpanSlowed(
                context.recentSpans(), context.baselineSpanAvg(), context.spikeMultiplier());
        return dependencies.collapseEvaluator().evaluate(resourceStatus, responseStatus);
    }

    @Override
    public void onAnomalyDetected(AnalysisContext context, AnalysisDependencies dependencies) {
        log.info("PerformanceCollapse 감지 app={}", context.appName());

        double avgCpu = context.recentMetrics().stream()
                .mapToDouble(MetricsSnapshot::cpuUsage).average().orElse(0.0);
        double avgHeap = context.recentMetrics().stream()
                .mapToDouble(MetricsSnapshot::heapUsed).average().orElse(0.0);
        double avgSpan = dependencies.appResponseTimeCalculator()
                .calculateAverage(context.recentSpans()).orElse(0.0);

        CollapseIncident incident = new CollapseIncident(
                context.appName(), Instant.now().minusSeconds(60), Instant.now(), Instant.now(),
                context.recentMetrics(), context.recentSpans(),
                context.baselineCpuAvg(), context.baselineHeapAvg(),
                context.baselineSpanAvg(), avgCpu, avgHeap, avgSpan);

        AiCallResult result = dependencies.ollamaAnalysisService().analyze(incident);
        dependencies.aiAnalysisResultPort().saveCollapseResult(result, incident);
    }

}