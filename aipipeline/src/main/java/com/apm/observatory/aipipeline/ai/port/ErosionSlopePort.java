package com.apm.observatory.aipipeline.ai.port;

import com.apm.observatory.aipipeline.analysis.model.SlopeRecord;

// 의도: slope 저장 책임을 AiAnalysisResultPort와 분리
// AiAnalysisResultPort = AI 분석 결과 저장
// ErosionSlopePort     = Erosion slope 값 저장 (분석 결과와 별개로 항상 저장)
public interface ErosionSlopePort {

    void save(SlopeRecord slopeRecord);

}