package com.apm.observatory.aipipeline.context.strategy;

import com.apm.observatory.aipipeline.analysis.model.SlopeRecord;
import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.context.model.AnalysisContext;
import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;

/**
 * 시계열 포인트 기반 추세 감지 전략의 계약.
 *
 * <p>누적 구간의 기울기로 완만한 변화를 판정하는 전략들이 구현한다.
 * 기울기는 호출자(TransferStep)가 {@link SlopeRecord}로 미리 계산해
 * 넘기므로, 전략은 중복 계산 없이 판정만 한다.
 */
public interface TrendDetectionStrategy {

    DetectionStatus detectTrend(SlopeRecord slopeRecord, AnalysisDependencies dependencies);

    void onTrendDetected(SlopeRecord slopeRecord, AnalysisContext context,
                         AnalysisDependencies dependencies);

    /**
     * 미감지 시 후속 처리. 기본은 아무것도 하지 않으며, 미감지 상태에서도
     * slope 저장이 필요한 전략만 오버라이드한다.
     */
    default void onTrendNotDetected(SlopeRecord slopeRecord, AnalysisDependencies dependencies) {}

}