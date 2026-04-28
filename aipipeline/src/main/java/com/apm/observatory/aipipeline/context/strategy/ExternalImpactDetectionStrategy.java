package com.apm.observatory.aipipeline.context.strategy;


import com.apm.observatory.aipipeline.analysis.model.ExternalImpactIncident;
import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.analysis.status.ResourceStatus;
import com.apm.observatory.aipipeline.analysis.status.ResponseStatus;
import com.apm.observatory.aipipeline.context.model.AnalysisContext;
import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.ExternalSpanSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.MetricsSnapshot;
import com.apm.observatory.aipipeline.ai.model.AiCallResult;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;

@Slf4j
public class ExternalImpactDetectionStrategy implements AnomalyDetectionStrategy {

    @Override
    public DetectionStatus detectAnomaly(AnalysisContext context, AnalysisDependencies dependencies) {
        List<ExternalImpactDataPort.MetricsSnapshot> extMetrics = toExtMetrics(context.recentMetrics());
        ResourceStatus resourceStatus = dependencies.externalImpactEvaluator().checkResourceStatus(
                extMetrics, context.cpuThreshold(), context.memoryThreshold());
        ResponseStatus responseStatus = dependencies.externalImpactEvaluator().checkExternalSpanStatus(
                context.recentExternalSpans(), context.baselineExternalAvg(),
                context.externalRatioMultiplier());
        return dependencies.externalImpactEvaluator().evaluate(resourceStatus, responseStatus);
    }

    @Override
    public void onAnomalyDetected(AnalysisContext context, AnalysisDependencies dependencies) {
        log.info("ExternalImpact 감지 app={}", context.appName());

        List<ExternalImpactDataPort.MetricsSnapshot> extMetrics = toExtMetrics(context.recentMetrics());
        double avgExt = context.recentExternalSpans().stream()
                .mapToDouble(ExternalSpanSnapshot::durationMs).average().orElse(0.0);
        String extHost = context.recentExternalSpans().stream()
                .map(ExternalSpanSnapshot::externalHost)
                .distinct().findFirst().orElse("unknown");

        ExternalImpactIncident incident = new ExternalImpactIncident(
                context.appName(), Instant.now().minusSeconds(60), Instant.now(), Instant.now(),
                extMetrics, context.recentExternalSpans(),
                context.baselineExternalAvg(), avgExt, extHost);

        AiCallResult result = dependencies.ollamaAnalysisService().analyze(incident);
        dependencies.aiAnalysisResultPort().saveExternalImpactResult(result, incident);
    }

    private List<ExternalImpactDataPort.MetricsSnapshot> toExtMetrics(
            List<MetricsSnapshot> metrics) {
        return metrics.stream()
                .map(m -> new ExternalImpactDataPort.MetricsSnapshot(
                        m.timestamp(), m.appName(), m.cpuUsage(),
                        m.heapUsed(), m.heapMax(), m.diskReadBytes(), m.diskWriteBytes()))
                .toList();
    }

}