package com.apm.observatory.aipipeline.analysis.evaluator;

import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.analysis.status.ResourceStatus;
import com.apm.observatory.aipipeline.analysis.status.ResponseStatus;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.MetricsSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.SpanSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.apm.observatory.aipipeline.analysis.status.ResourceStatus.*;

@Slf4j
@Component
public class PerformanceCollapseEvaluator {

    public ResourceStatus isResourceSpiked(List<MetricsSnapshot> recentMetrics,
                                    double baselineCpuAvg,
                                    double baselineHeapAvg,
                                    double spikeMultiplier) {
        if (recentMetrics.isEmpty()) {
            log.warn("자원 판단 스킵 - 수집된 Metrics 데이터 없음");
            return NODATA;
        }

        // 단일 순회로 CPU, Heap 합계 동시 계산
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

    public ResponseStatus isSpanSlowed(List<SpanSnapshot> recentSpans,
                                double baselineSpanAvg,
                                double spikeMultiplier) {
        if (recentSpans.isEmpty()) {
            log.warn("응답시간 판단 스킵 - 수집된 Span 데이터 없음");
            return ResponseStatus.NODATA;
        }

        double sumDuration = 0.0;
        for (SpanSnapshot s : recentSpans) {
            sumDuration += s.durationMs();
        }
        double avgDuration = sumDuration / recentSpans.size();

        log.debug("응답시간 판단 avgDuration={}ms, baseline={}ms", avgDuration, baselineSpanAvg);

        if (avgDuration > baselineSpanAvg * spikeMultiplier) {
            return ResponseStatus.SLOWED;
        }
        return ResponseStatus.NORMAL;
    }

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