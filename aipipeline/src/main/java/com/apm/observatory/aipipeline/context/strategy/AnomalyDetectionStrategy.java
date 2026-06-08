package com.apm.observatory.aipipeline.context.strategy;

import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.context.model.AnalysisContext;
import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;

/**
 * 순간 스냅샷 기반 이상 감지 전략의 계약.
 *
 * <p>임계값 대비로 현재 구간의 급등·급락을 판정하는 전략들이 구현한다.
 * 판정({@code detectAnomaly})과 감지 시 후속 처리({@code onAnomalyDetected})를
 * 나눈다.
 */
public interface AnomalyDetectionStrategy {

    DetectionStatus detectAnomaly(AnalysisContext context, AnalysisDependencies dependencies);

    void onAnomalyDetected(AnalysisContext context, AnalysisDependencies dependencies);

}