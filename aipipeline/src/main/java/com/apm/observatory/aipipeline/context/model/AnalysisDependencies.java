package com.apm.observatory.aipipeline.context.model;

import com.apm.observatory.aipipeline.ai.port.AiAnalysisResultPort;
import com.apm.observatory.aipipeline.ai.port.ErosionSlopePort;
import com.apm.observatory.aipipeline.ai.service.OllamaAnalysisService;
import com.apm.observatory.aipipeline.analysis.evaluator.ExternalImpactEvaluator;
import com.apm.observatory.aipipeline.analysis.calculator.AppResponseTimeCalculator;
import com.apm.observatory.aipipeline.analysis.evaluator.PerformanceCollapseEvaluator;
import com.apm.observatory.aipipeline.analysis.evaluator.PerformanceErosionEvaluator;
import com.apm.observatory.aipipeline.context.strategy.AnomalyDetectionStrategy;
import com.apm.observatory.aipipeline.context.strategy.TrendDetectionStrategy;

import java.util.List;

/**
 * 분석에 필요한 협력 객체(평가기·계산기·AI 서비스·저장 Port·전략 목록)를
 * 한 묶음으로 들고 다니는 의존성 컨테이너. PerformanceContextManager가
 * 기동 시 한 번 구성해 매 사이클 재사용한다.
 *
 * <p>전략들은 Spring 빈이 아니라 {@code new}로 생성되는 일반 객체라 외부
 * 의존성을 직접 주입받지 못한다. 그래서 전략이 쓰는 Port까지 이 묶음에
 * 담아, 전략이 dependencies를 통해서만 외부에 접근하도록 한다.
 */
public record AnalysisDependencies(
        PerformanceCollapseEvaluator collapseEvaluator,
        AppResponseTimeCalculator appResponseTimeCalculator,
        ExternalImpactEvaluator externalImpactEvaluator,
        PerformanceErosionEvaluator erosionEvaluator,
        OllamaAnalysisService ollamaAnalysisService,
        AiAnalysisResultPort aiAnalysisResultPort,
        ErosionSlopePort erosionSlopePort,
        List<AnomalyDetectionStrategy> detectionStrategies,
        List<TrendDetectionStrategy> trendStrategies
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private PerformanceCollapseEvaluator collapseEvaluator;
        private AppResponseTimeCalculator appResponseTimeCalculator;
        private ExternalImpactEvaluator externalImpactEvaluator;
        private PerformanceErosionEvaluator erosionEvaluator;
        private OllamaAnalysisService ollamaAnalysisService;
        private AiAnalysisResultPort aiAnalysisResultPort;
        private ErosionSlopePort erosionSlopePort;
        private List<AnomalyDetectionStrategy> detectionStrategies;
        private List<TrendDetectionStrategy> trendStrategies;

        public Builder collapseEvaluator(PerformanceCollapseEvaluator collapseEvaluator) {
            this.collapseEvaluator = collapseEvaluator;
            return this;
        }

        public Builder appResponseTimeCalculator(AppResponseTimeCalculator appResponseTimeCalculator) {
            this.appResponseTimeCalculator = appResponseTimeCalculator;
            return this;
        }

        public Builder externalImpactEvaluator(ExternalImpactEvaluator externalImpactEvaluator) {
            this.externalImpactEvaluator = externalImpactEvaluator;
            return this;
        }

        public Builder erosionEvaluator(PerformanceErosionEvaluator erosionEvaluator) {
            this.erosionEvaluator = erosionEvaluator;
            return this;
        }

        public Builder ollamaAnalysisService(OllamaAnalysisService ollamaAnalysisService) {
            this.ollamaAnalysisService = ollamaAnalysisService;
            return this;
        }

        public Builder aiAnalysisResultPort(AiAnalysisResultPort aiAnalysisResultPort) {
            this.aiAnalysisResultPort = aiAnalysisResultPort;
            return this;
        }

        public Builder erosionSlopePort(ErosionSlopePort erosionSlopePort) {
            this.erosionSlopePort = erosionSlopePort;
            return this;
        }

        public Builder detectionStrategies(List<AnomalyDetectionStrategy> detectionStrategies) {
            this.detectionStrategies = detectionStrategies;
            return this;
        }

        public Builder trendStrategies(List<TrendDetectionStrategy> trendStrategies) {
            this.trendStrategies = trendStrategies;
            return this;
        }

        public AnalysisDependencies build() {
            return new AnalysisDependencies(
                    collapseEvaluator, appResponseTimeCalculator, externalImpactEvaluator, erosionEvaluator,
                    ollamaAnalysisService, aiAnalysisResultPort,
                    erosionSlopePort,
                    detectionStrategies, trendStrategies);
        }
    }

}