package com.apm.observatory.aipipeline.analysis.prompt;

import com.apm.observatory.aipipeline.analysis.model.CollapseIncident;
import com.apm.observatory.aipipeline.analysis.model.ErosionIncident;
import com.apm.observatory.aipipeline.analysis.model.ExternalImpactIncident;
import com.apm.observatory.aipipeline.config.AiPipelineConfig.Prompt;
import org.springframework.stereotype.Component;

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