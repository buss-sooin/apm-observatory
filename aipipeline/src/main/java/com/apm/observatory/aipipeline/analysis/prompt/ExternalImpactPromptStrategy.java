package com.apm.observatory.aipipeline.analysis.prompt;

import com.apm.observatory.aipipeline.analysis.model.ExternalImpactIncident;
import com.apm.observatory.aipipeline.config.AiPipelineConfig.Prompt;

/** 외부 영향 사건용 프롬프트 전략. 사용자 프롬프트에 외부 구간 지연 수치를 채운다. */
public class ExternalImpactPromptStrategy implements PromptBuildStrategy {

    private final ExternalImpactIncident incident;
    private final Prompt prompt;

    public ExternalImpactPromptStrategy(ExternalImpactIncident incident, Prompt prompt) {
        this.incident = incident;
        this.prompt = prompt;
    }

    @Override
    public Prompt getPrompt() {
        return prompt;
    }

    @Override
    public String buildUserPrompt() {
        return String.format("""
            앱: %s
            분석 시간: %s ~ %s
            감지된 상황: 자원 정상 AND 외부 API 응답 급등
            외부 호스트: %s
            평균 외부 Span 응답시간: %.0fms (기준: %.0fms)
            """,
                incident.appName(),
                incident.analysisStart(),
                incident.analysisEnd(),
                incident.externalHost(),
                incident.avgExternalDuration(), incident.baselineExternalAvg()
        );
    }

}