package com.apm.observatory.aipipeline.context.strategy;

import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.context.model.AnalysisContext;
import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;

// 의도: 순간 스냅샷 기반 이상(Anomaly) 감지 전략의 계약
// 임계값 비교로 지금 이 순간 급등/급락을 감지하는 전략들이 구현
public interface AnomalyDetectionStrategy {

    DetectionStatus detectAnomaly(AnalysisContext context, AnalysisDependencies dependencies);

    void onAnomalyDetected(AnalysisContext context, AnalysisDependencies dependencies);

}