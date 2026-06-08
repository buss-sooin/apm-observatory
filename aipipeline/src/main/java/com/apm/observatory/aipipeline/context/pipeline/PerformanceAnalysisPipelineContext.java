package com.apm.observatory.aipipeline.context.pipeline;

import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;
import com.apm.observatory.aipipeline.performance.model.PerformanceTrend;

/**
 * 성능 분석 파이프라인의 진입점이자 Step Builder 조립의 시작.
 *
 * <p>분석 한 사이클을 {@code configure → loadBaseline → loadSnapshot →
 * analyzeAnomalies → transferToTrend} 다섯 단계로 나누고, 각 단계는 다음
 * 단계 객체만 반환해 호출 순서를 컴파일 타임에 강제한다. 단계 사이로
 * 누적되는 상태(appName·trend·threshold·baseline·snapshot)는 단계 객체가
 * 들고 넘긴다.
 *
 * <p>데이터 적재 단계(loadBaseline·loadSnapshot)는 적재 방식을 loader
 * 람다로 주입받고, 단계 자신은 호출·전달만 맡는다.
 */
public class PerformanceAnalysisPipelineContext {

    /**
     * 파이프라인을 시작한다.
     *
     * @return 첫 단계인 {@link ConfigureStep}
     */
    public static ConfigureStep startWith(
            AnalysisDependencies dependencies,
            String appName,
            PerformanceTrend trend) {
        return new ConfigureStep(dependencies, appName, trend);
    }

}