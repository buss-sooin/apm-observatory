package com.apm.observatory.aipipeline.ai.port;

import com.apm.observatory.aipipeline.analysis.model.SlopeRecord;

/**
 * erosion slope 저장 계약. 분석 결과 저장(AiAnalysisResultPort)과 달리,
 * slope는 감지 여부와 무관하게 항상 저장하므로 별도 Port로 둔다.
 */
public interface ErosionSlopePort {

    void save(SlopeRecord slopeRecord);

}