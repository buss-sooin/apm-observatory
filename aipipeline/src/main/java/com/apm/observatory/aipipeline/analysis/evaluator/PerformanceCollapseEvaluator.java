package com.apm.observatory.aipipeline.analysis.evaluator;

import com.apm.observatory.aipipeline.analysis.calculator.AppResponseTimeCalculator;
import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.analysis.status.ResourceStatus;
import com.apm.observatory.aipipeline.analysis.status.ResponseStatus;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.MetricsSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.SpanSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.OptionalDouble;

import static com.apm.observatory.aipipeline.analysis.status.ResourceStatus.*;

/**
 * 성능 붕괴 판정기. 자원(cpu·heap)과 응답시간이 모두 baseline 대비 급등했는지 보고,
 * 둘 다 이상일 때만 붕괴로 판정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceCollapseEvaluator {

    private final AppResponseTimeCalculator appResponseTimeCalculator;

    /** 자원(cpu·heap) 평균이 baseline의 {@code spikeMultiplier}배를 넘으면 SPIKED. 수집 metrics 없으면 NODATA. */
    public ResourceStatus isResourceSpiked(List<MetricsSnapshot> recentMetrics,
                                    double baselineCpuAvg,
                                    double baselineHeapAvg,
                                    double spikeMultiplier) {
        if (recentMetrics.isEmpty()) {
            log.warn("자원 판단 스킵 - 수집된 Metrics 데이터 없음");
            return NODATA;
        }

        double sumCpu = 0.0, sumHeap = 0.0;
        for (MetricsSnapshot m : recentMetrics) {
            sumCpu += m.cpuUsage();
            sumHeap += m.heapUsed();
        }
        double avgCpu = sumCpu / recentMetrics.size();
        double avgHeap = sumHeap / recentMetrics.size();

        log.debug("자원 판단 avgCpu={}, avgHeap={}, baselineCpu={}, baselineHeap={}",
                avgCpu, avgHeap, baselineCpuAvg, baselineHeapAvg);

        if (avgCpu > baselineCpuAvg * spikeMultiplier ||
                avgHeap > baselineHeapAvg * spikeMultiplier) {
            return SPIKED;
        }
        return NORMAL;
    }

    /**
     * 외부 호출을 뺀 trace 응답시간 평균이 평소 대비 급등했는지 판정한다.
     *
     * <p>{@link AppResponseTimeCalculator}로 구한 측정 평균이 baseline의
     * {@code spikeMultiplier}배를 넘으면 SLOWED로 본다. 수집된 span이 없거나
     * 응답시간을 계산할 수 있는 trace가 없으면 NODATA를 돌려준다.
     *
     * @param recentSpans     최근 구간에서 수집한 span 목록(타입 혼재)
     * @param baselineSpanAvg 평소 기준 응답시간 평균(ms)
     * @param spikeMultiplier 급등 판정 배수
     * @return SLOWED / NORMAL / NODATA
     */
    public ResponseStatus isSpanSlowed(List<SpanSnapshot> recentSpans,
                                double baselineSpanAvg,
                                double spikeMultiplier) {
        if (recentSpans.isEmpty()) {
            log.warn("응답시간 판단 스킵 - 수집된 Span 데이터 없음");
            return ResponseStatus.NODATA;
        }

        OptionalDouble avgResponseTime = appResponseTimeCalculator.calculateAverage(recentSpans);
        if (avgResponseTime.isEmpty()) {
            log.warn("응답시간 판단 스킵 - ROOT가 있는 trace 없음");
            return ResponseStatus.NODATA;
        }
        double avgDuration = avgResponseTime.getAsDouble();

        log.debug("응답시간 판단 avgDuration={}ms, baseline={}ms", avgDuration, baselineSpanAvg);

        if (avgDuration > baselineSpanAvg * spikeMultiplier) {
            return ResponseStatus.SLOWED;
        }
        return ResponseStatus.NORMAL;
    }

    /** 자원·응답 상태를 합쳐 판정. NODATA 포함 시 UNDETERMINABLE, 둘 다 이상(SPIKED·SLOWED)이면 DETECTED, 그 밖이면 NOT_DETECTED. */
    public DetectionStatus evaluate(ResourceStatus resourceStatus,
                                    ResponseStatus responseStatus) {
        if (resourceStatus == NODATA ||
                responseStatus == ResponseStatus.NODATA) {
            log.warn("판단 불가 - NODATA 상태 포함 resource={}, response={}",
                    resourceStatus, responseStatus);
            return DetectionStatus.UNDETERMINABLE;
        }

        DetectionStatus result = (resourceStatus == SPIKED &&
                responseStatus == ResponseStatus.SLOWED)
                ? DetectionStatus.DETECTED
                : DetectionStatus.NOT_DETECTED;

        log.info("PerformanceCollapse 판단 완료 resource={}, response={}, result={}",
                resourceStatus, responseStatus, result);

        return result;
    }

}