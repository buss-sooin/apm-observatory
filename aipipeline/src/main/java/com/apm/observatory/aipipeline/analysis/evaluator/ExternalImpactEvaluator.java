package com.apm.observatory.aipipeline.analysis.evaluator;

import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.analysis.status.ResourceStatus;
import com.apm.observatory.aipipeline.analysis.status.ResponseStatus;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.ExternalSpanSnapshot;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.MetricsSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 외부 영향 판정기. 자원은 정상(임계값 이하)인데 외부 연동 구간만 baseline 대비
 * 느려졌는지 보고, 그럴 때만 외부 영향으로 판정한다.
 */
@Slf4j
@Component
public class ExternalImpactEvaluator {

    /** 자원(cpu·메모리율) 평균이 임계값을 넘으면 SPIKED. 수집 metrics 없으면 NODATA. */
    public ResourceStatus checkResourceStatus(List<MetricsSnapshot> recentMetrics,
                                       double cpuThreshold,
                                       double memoryThreshold) {
        if (recentMetrics.isEmpty()) {
            log.warn("자원 판단 스킵 - 수집된 Metrics 데이터 없음");
            return ResourceStatus.NODATA;
        }

        double sumCpu = 0.0, sumMemoryRate = 0.0;
        for (MetricsSnapshot m : recentMetrics) {
            sumCpu += m.cpuUsage();
            sumMemoryRate += (double) m.heapUsed() / m.heapMax() * 100.0;
        }
        double avgCpu = sumCpu / recentMetrics.size();
        double avgMemoryRate = sumMemoryRate / recentMetrics.size();

        log.debug("자원 판단 avgCpu={}, avgMemoryRate={}, cpuThreshold={}, memoryThreshold={}",
                avgCpu, avgMemoryRate, cpuThreshold, memoryThreshold);

        if (avgCpu > cpuThreshold || avgMemoryRate > memoryThreshold) {
            return ResourceStatus.SPIKED;
        }
        return ResourceStatus.NORMAL;
    }

    /** EXTERNAL span 평균이 baseline의 {@code externalRatioMultiplier}배를 넘으면 SLOWED. 수집 span 없으면 NODATA. */
    public ResponseStatus checkExternalSpanStatus(List<ExternalSpanSnapshot> recentExternalSpans,
                                           double baselineExternalAvg,
                                           double externalRatioMultiplier) {
        if (recentExternalSpans.isEmpty()) {
            log.warn("외부 Span 판단 스킵 - 수집된 External Span 데이터 없음");
            return ResponseStatus.NODATA;
        }

        double sumDuration = 0.0;
        for (ExternalSpanSnapshot s : recentExternalSpans) {
            sumDuration += s.durationMs();
        }
        double avgDuration = sumDuration / recentExternalSpans.size();

        log.debug("외부 Span 판단 avgDuration={}ms, baseline={}ms", avgDuration, baselineExternalAvg);

        if (avgDuration > baselineExternalAvg * externalRatioMultiplier) {
            return ResponseStatus.SLOWED;
        }
        return ResponseStatus.NORMAL;
    }

    /** 자원이 정상(NORMAL)인데 외부 span이 SLOWED일 때만 DETECTED. NODATA 포함 시 UNDETERMINABLE. */
    public DetectionStatus evaluate(ResourceStatus resourceStatus,
                                    ResponseStatus responseStatus) {
        if (resourceStatus == ResourceStatus.NODATA ||
                responseStatus == ResponseStatus.NODATA) {
            log.warn("판단 불가 - NODATA 상태 포함 resource={}, response={}",
                    resourceStatus, responseStatus);
            return DetectionStatus.UNDETERMINABLE;
        }

        DetectionStatus result = (resourceStatus == ResourceStatus.NORMAL &&
                responseStatus == ResponseStatus.SLOWED)
                ? DetectionStatus.DETECTED
                : DetectionStatus.NOT_DETECTED;

        log.info("ExternalImpact 판단 완료 resource={}, response={}, result={}",
                resourceStatus, responseStatus, result);

        return result;
    }

}