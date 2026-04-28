package com.apm.observatory.aipipeline.ai.port;

import com.apm.observatory.aipipeline.ai.model.AiCallResult;
import com.apm.observatory.aipipeline.analysis.model.CollapseIncident;
import com.apm.observatory.aipipeline.analysis.model.ErosionIncident;
import com.apm.observatory.aipipeline.analysis.model.ExternalImpactIncident;

public interface AiAnalysisResultPort {

    void saveCollapseResult(AiCallResult aiCallResult, CollapseIncident incident);

    void saveErosionResult(AiCallResult aiCallResult, ErosionIncident incident);

    void saveExternalImpactResult(AiCallResult aiCallResult, ExternalImpactIncident incident);

}