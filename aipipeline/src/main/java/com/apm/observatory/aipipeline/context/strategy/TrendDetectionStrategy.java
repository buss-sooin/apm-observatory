package com.apm.observatory.aipipeline.context.strategy;

import com.apm.observatory.aipipeline.analysis.model.SlopeRecord;
import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.context.model.AnalysisContext;
import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;

// 의도: 시계열 포인트 기반 추세(Trend) 감지 전략의 계약
// 기울기 계산으로 시간 흐름에 따른 완만한 변화를 감지하는 전략들이 구현
// SlopeRecord는 TransferStep에서 미리 계산해서 전달 → 전략 내 중복 계산 제거
public interface TrendDetectionStrategy {

    DetectionStatus detectTrend(SlopeRecord slopeRecord, AnalysisDependencies dependencies);

    void onTrendDetected(SlopeRecord slopeRecord, AnalysisContext context,
                         AnalysisDependencies dependencies);

    // 의도: DETECTED/NOT_DETECTED 무관하게 slope 저장이 필요한 전략만 오버라이드
    // 기본 구현은 아무것도 안 함 → Erosion 외 전략은 건드리지 않아도 됨
    default void onTrendNotDetected(SlopeRecord slopeRecord, AnalysisDependencies dependencies) {}

}