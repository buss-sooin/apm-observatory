package com.apm.observatory.apiserver.metrics.adapter;

import com.apm.observatory.apiserver.config.repository.ThresholdConfigRepository;
import com.apm.observatory.apiserver.metrics.repository.ErosionTrendSlopeRepository;
import com.apm.observatory.apiserver.metrics.repository.MetricsRepository;
import com.apm.observatory.apiserver.metrics.model.MetricsModel.CurrentMetrics;
import com.apm.observatory.apiserver.metrics.model.MetricsModel.SummaryMetrics;
import com.apm.observatory.apiserver.metrics.model.MetricsModel.TrendPoint;
import com.apm.observatory.apiserver.metrics.port.MetricsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * metrics·threshold_config·erosion_trend_slopes 세 테이블을 조합해 자원 현황·추세·요약을
 * 만든다(MetricsPort 구현). 외부 경계(DB) 값의 null은 여기서 막는다. 집계 함수와 래퍼 타입은
 * 결과가 없으면 null이라, Optional.ofNullable().orElse(기본값)으로 기본값으로 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class MetricsAdapter implements MetricsPort {

    private final MetricsRepository metricsRepository;
    private final ThresholdConfigRepository thresholdConfigRepository;
    private final ErosionTrendSlopeRepository erosionTrendSlopeRepository;

    @Override
    public Optional<CurrentMetrics> snapshotCurrent(String appName) {
        return metricsRepository.findLatestByAppName(appName)
                .map(m -> new CurrentMetrics(
                        m.getId().getTimestamp(),
                        m.getId().getAppName(),
                        Optional.ofNullable(m.getCpuUsage()).orElse(0.0),
                        Optional.ofNullable(m.getHeapUsed()).orElse(0L),
                        Optional.ofNullable(m.getHeapMax()).orElse(0L),
                        Optional.ofNullable(m.getThreadCount()).orElse(0)
                ));
    }

    @Override
    public List<TrendPoint> traceTrend(String appName, Instant startTime, Instant endTime) {
        return metricsRepository.findByAppNameAndTimestampBetween(appName, startTime, endTime)
                .stream()
                .map(m -> new TrendPoint(
                        m.getId().getTimestamp(),
                        Optional.ofNullable(m.getCpuUsage()).orElse(0.0),
                        Optional.ofNullable(m.getHeapUsed()).orElse(0L),
                        Optional.ofNullable(m.getHeapMax()).orElse(0L)
                ))
                .toList();
    }

    /**
     * 세 테이블을 조합해 SummaryMetrics를 만든다. threshold가 없으면 empty를 돌려 Controller가
     * 404로 응답한다. 임계값 대비 수준(%)은 위젯에서 위험도를 표시하는 값이고, slope가 아직
     * 없으면(Erosion 판단 전) 0.0으로 둬 추세 없음으로 표시한다.
     */
    @Override
    public Optional<SummaryMetrics> summarizePerformance(String appName, Instant startTime, Instant endTime) {
        return thresholdConfigRepository.findByAppName(appName)
                .map(threshold -> {
                    double avgCpu = Optional.ofNullable(
                                    metricsRepository.findAvgCpuUsage(appName, startTime, endTime))
                            .orElse(0.0);
                    double avgHeap = Optional.ofNullable(
                                    metricsRepository.findAvgHeapUsagePercent(appName, startTime, endTime))
                            .orElse(0.0);

                    double cpuThreshold = Optional.ofNullable(threshold.getCpuThreshold()).orElse(80.0);
                    double memoryThreshold = Optional.ofNullable(threshold.getMemoryThreshold()).orElse(80.0);

                    double cpuUsagePercent = cpuThreshold > 0 ? avgCpu / cpuThreshold * 100 : 0.0;
                    double heapUsagePercent = memoryThreshold > 0 ? avgHeap / memoryThreshold * 100 : 0.0;

                    double resourceSlope = erosionTrendSlopeRepository.findLatestByAppName(appName)
                            .map(e -> e.getResourceSlope()).orElse(0.0);
                    double responseSlope = erosionTrendSlopeRepository.findLatestByAppName(appName)
                            .map(e -> e.getResponseSlope()).orElse(0.0);

                    return new SummaryMetrics(
                            avgCpu, avgHeap,
                            cpuThreshold, memoryThreshold,
                            cpuUsagePercent, heapUsagePercent,
                            resourceSlope, responseSlope
                    );
                });
    }

}