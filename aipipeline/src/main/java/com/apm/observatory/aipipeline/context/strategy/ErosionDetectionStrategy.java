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

@Slf4j
public class ErosionDetectionStrategy implements TrendDetectionStrategy {

    // 의도: SlopeRecord에 slope 계산값과 판단 기준값(slopeMinPositive) 모두 포함
    // → TransferStep에서 한 번만 계산하고 전달 → 중복 계산 제거
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

    // 의도: NOT_DETECTED일 때도 slope 저장 → 그래프에서 추세 흐름 시각화 가능
    @Override
    public void onTrendNotDetected(SlopeRecord slopeRecord, AnalysisDependencies dependencies) {
        log.debug("PerformanceErosion 미감지 slope 저장 app={}", slopeRecord.appName());
        dependencies.erosionSlopePort().save(slopeRecord);
    }

}