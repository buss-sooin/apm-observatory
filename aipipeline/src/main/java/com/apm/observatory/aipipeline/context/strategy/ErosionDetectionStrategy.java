package com.apm.observatory.aipipeline.context.strategy;


import com.apm.observatory.aipipeline.analysis.model.ErosionDataPoint;
import com.apm.observatory.aipipeline.analysis.model.ErosionIncident;
import com.apm.observatory.aipipeline.analysis.model.SlopeRecord;
import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.analysis.status.TrendStatus;
import com.apm.observatory.aipipeline.context.model.AnalysisContext;
import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;
import com.apm.observatory.aipipeline.ai.model.AiCallResult;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;

/** 누적 추세의 기울기로 성능 침식(erosion)을 판정하는 전략. 미감지 시에도 slope를 저장해 추세 흐름을 남긴다. */
@Slf4j
public class ErosionDetectionStrategy implements TrendDetectionStrategy {

    @Override
    public DetectionStatus detectTrend(SlopeRecord slopeRecord, AnalysisDependencies dependencies) {
        TrendStatus resourceTrend = dependencies.erosionEvaluator()
                .toTrendStatus(slopeRecord.resourceSlope(), slopeRecord.slopeMinPositive());
        TrendStatus responseTrend = dependencies.erosionEvaluator()
                .toTrendStatus(slopeRecord.responseSlope(), slopeRecord.slopeMinPositive());
        return dependencies.erosionEvaluator().evaluate(resourceTrend, responseTrend);
    }

    @Override
    public void onTrendDetected(SlopeRecord slopeRecord, AnalysisContext context,
                                AnalysisDependencies dependencies) {
        log.info("PerformanceErosion 감지 app={}", context.appName());

        List<ErosionDataPoint> points = context.trend().getPoints();
        ErosionIncident incident = new ErosionIncident(
                context.appName(),
                context.trend().getContextStartTime(),
                Instant.now(),
                Instant.now(),
                points,
                slopeRecord.resourceSlope(),
                slopeRecord.responseSlope());

        AiCallResult result = dependencies.ollamaAnalysisService().analyze(incident);
        dependencies.aiAnalysisResultPort().saveErosionResult(result, incident);
    }

    @Override
    public void onTrendNotDetected(SlopeRecord slopeRecord, AnalysisDependencies dependencies) {
        log.debug("PerformanceErosion 미감지 slope 저장 app={}", slopeRecord.appName());
        dependencies.erosionSlopePort().save(slopeRecord);
    }

}