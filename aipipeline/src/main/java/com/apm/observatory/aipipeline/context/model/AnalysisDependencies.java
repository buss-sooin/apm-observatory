package com.apm.observatory.aipipeline.context.model;

import com.apm.observatory.aipipeline.ai.port.AiAnalysisResultPort;
import com.apm.observatory.aipipeline.ai.port.ErosionSlopePort;
import com.apm.observatory.aipipeline.ai.service.OllamaAnalysisService;
import com.apm.observatory.aipipeline.analysis.evaluator.ExternalImpactEvaluator;
import com.apm.observatory.aipipeline.analysis.evaluator.PerformanceCollapseEvaluator;
import com.apm.observatory.aipipeline.analysis.evaluator.PerformanceErosionEvaluator;
import com.apm.observatory.aipipeline.context.strategy.AnomalyDetectionStrategy;
import com.apm.observatory.aipipeline.context.strategy.TrendDetectionStrategy;

import java.util.List;

public record AnalysisDependencies(
        PerformanceCollapseEvaluator collapseEvaluator,
        ExternalImpactEvaluator externalImpactEvaluator,
        PerformanceErosionEvaluator erosionEvaluator,
        OllamaAnalysisService ollamaAnalysisService,
        AiAnalysisResultPort aiAnalysisResultPort,
        // 의도: ErosionDetectionStrategy가 new로 생성되는 일반 객체라 Spring 주입 불가
        // dependencies를 통해서만 외부 의존성에 접근하는 구조 일관성 유지
        ErosionSlopePort erosionSlopePort,
        List<AnomalyDetectionStrategy> detectionStrategies,
        List<TrendDetectionStrategy> trendStrategies
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private PerformanceCollapseEvaluator collapseEvaluator;
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
                    collapseEvaluator, externalImpactEvaluator, erosionEvaluator,
                    ollamaAnalysisService, aiAnalysisResultPort,
                    erosionSlopePort,
                    detectionStrategies, trendStrategies);
        }
    }

}