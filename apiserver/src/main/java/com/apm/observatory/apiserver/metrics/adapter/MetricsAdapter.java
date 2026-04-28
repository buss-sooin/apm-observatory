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
                        // 의도: null 방어 - DB에 데이터 없으면 0.0 기본값
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

    @Override
    public Optional<SummaryMetrics> summarizePerformance(String appName, Instant startTime, Instant endTime) {
        // 의도: 세 테이블을 조합해서 SummaryMetrics 생성
        // threshold 없으면 empty → Controller에서 404 처리
        return thresholdConfigRepository.findByAppName(appName)
                .map(threshold -> {
                    // 의도: AVG 결과 null 방어 - 데이터 없으면 0.0
                    double avgCpu = Optional.ofNullable(
                                    metricsRepository.findAvgCpuUsage(appName, startTime, endTime))
                            .orElse(0.0);
                    double avgHeap = Optional.ofNullable(
                                    metricsRepository.findAvgHeapUsagePercent(appName, startTime, endTime))
                            .orElse(0.0);

                    double cpuThreshold = Optional.ofNullable(threshold.getCpuThreshold()).orElse(80.0);
                    double memoryThreshold = Optional.ofNullable(threshold.getMemoryThreshold()).orElse(80.0);

                    // 의도: 임계값 대비 현재 수준 (%) - 위젯에서 "얼마나 위험한가" 표시용
                    double cpuUsagePercent = cpuThreshold > 0 ? avgCpu / cpuThreshold * 100 : 0.0;
                    double heapUsagePercent = memoryThreshold > 0 ? avgHeap / memoryThreshold * 100 : 0.0;

                    // 의도: slope 없으면 0.0 기본값 → 아직 Erosion 판단 전이면 추세 없음으로 표시
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