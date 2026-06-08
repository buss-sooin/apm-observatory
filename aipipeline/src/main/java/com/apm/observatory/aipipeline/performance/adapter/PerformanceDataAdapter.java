package com.apm.observatory.aipipeline.performance.adapter;

import com.apm.observatory.aipipeline.performance.repository.MetricsRepository;
import com.apm.observatory.aipipeline.performance.repository.SpanRepository;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** {@link PerformanceDataPort} 구현. metrics·span 테이블을 조회해 스냅샷으로 옮기고, 집계 결과가 null이면 0으로 방어한다. */
@Component
@RequiredArgsConstructor
public class PerformanceDataAdapter implements PerformanceDataPort {

    private final MetricsRepository metricsRepository;
    private final SpanRepository spanRepository;

    @Override
    public List<MetricsSnapshot> getRecentMetrics(String appName, Instant start, Instant end) {
        return metricsRepository.findByAppNameAndTimestampBetween(appName, start, end)
                .stream()
                .map(e -> new MetricsSnapshot(
                        e.getTimestamp(),
                        e.getAppName(),
                        e.getCpuUsage(),
                        e.getHeapUsed(),
                        e.getHeapMax(),
                        e.getDiskReadBytes(),
                        e.getDiskWriteBytes()
                ))
                .toList();
    }

    @Override
    public Double getBaselineCpuAvg(String appName, Instant start, Instant end) {
        return Optional.ofNullable(metricsRepository.findAvgCpuUsage(appName, start, end))
                .orElse(0.0);
    }

    @Override
    public Double getBaselineHeapAvg(String appName, Instant start, Instant end) {
        return Optional.ofNullable(metricsRepository.findAvgHeapUsed(appName, start, end))
                .orElse(0.0);
    }

    @Override
    public List<SpanSnapshot> getRecentSpans(String appName, Instant start, Instant end) {
        return spanRepository.findByAppNameAndStartTimeBetween(appName, start, end)
                .stream()
                .map(e -> new SpanSnapshot(
                        e.getSpanId(),
                        e.getTraceId(),
                        e.getAppName(),
                        e.getSpanType(),
                        e.getDurationMs(),
                        e.getStartTime()
                ))
                .toList();
    }

}