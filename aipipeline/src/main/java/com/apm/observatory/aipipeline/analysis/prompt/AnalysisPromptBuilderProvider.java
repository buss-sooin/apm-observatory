package com.apm.observatory.aipipeline.analysis.prompt;

import com.apm.observatory.aipipeline.analysis.model.CollapseIncident;
import com.apm.observatory.aipipeline.analysis.model.ErosionIncident;
import com.apm.observatory.aipipeline.analysis.model.ExternalImpactIncident;
import com.apm.observatory.aipipeline.config.AiPipelineConfig.Prompt;
import org.springframework.stereotype.Component;

/**
 * 사건 타입에 맞는 {@link PromptBuildStrategy}를 만들어 주는 팩토리.
 * 오버로딩으로 incident 종류별 전략을 고른다.
 */
@Component
public class AnalysisPromptBuilderProvider {

    public static PromptBuildStrategy of(CollapseIncident incident, Prompt prompt) {
        return new CollapsePromptStrategy(incident, prompt);
    }

    public static PromptBuildStrategy of(ErosionIncident incident, Prompt prompt) {
        return new ErosionPromptStrategy(incident, prompt);
    }

    public static PromptBuildStrategy of(ExternalImpactIncident incident, Prompt prompt) {
        return new ExternalImpactPromptStrategy(incident, prompt);
    }

}