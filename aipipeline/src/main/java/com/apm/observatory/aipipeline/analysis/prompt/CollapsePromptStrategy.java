package com.apm.observatory.aipipeline.analysis.prompt;

import com.apm.observatory.aipipeline.analysis.model.CollapseIncident;
import com.apm.observatory.aipipeline.config.AiPipelineConfig.Prompt;

public class CollapsePromptStrategy implements PromptBuildStrategy {

    private final CollapseIncident incident;
    private final Prompt prompt;

    public CollapsePromptStrategy(CollapseIncident incident, Prompt prompt) {
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
            감지된 상황: 자원 급등 AND 응답시간 급등
            평균 CPU: %.1f%% (기준: %.1f%%)
            평균 Heap: %.0f bytes (기준: %.0f bytes)
            평균 응답시간: %.0fms (기준: %.0fms)
            """,
                incident.appName(),
                incident.analysisStart(),
                incident.analysisEnd(),
                incident.avgCpu(), incident.baselineCpuAvg(),
                incident.avgHeap(), incident.baselineHeapAvg(),
                incident.avgSpanDuration(), incident.baselineSpanAvg()
        );
    }

}