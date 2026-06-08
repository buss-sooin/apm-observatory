package com.apm.observatory.aipipeline.analysis.prompt;

import com.apm.observatory.aipipeline.analysis.model.ErosionIncident;
import com.apm.observatory.aipipeline.config.AiPipelineConfig.Prompt;

/** 침식 사건용 프롬프트 전략. 사용자 프롬프트에 추세 기울기 수치를 채운다. */
public class ErosionPromptStrategy implements PromptBuildStrategy {

    private final ErosionIncident incident;
    private final Prompt prompt;

    public ErosionPromptStrategy(ErosionIncident incident, Prompt prompt) {
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
                감지된 상황: 자원과 응답시간 완만한 동반 상승 추세
                자원 기울기: %.3f
                응답시간 기울기: %.3f
                누적 데이터 포인트: %d개
                """,
                incident.appName(),
                incident.analysisStart(),
                incident.analysisEnd(),
                incident.resourceSlope(),
                incident.responseSlope(),
                incident.trendPoints().size()
        );
    }

}