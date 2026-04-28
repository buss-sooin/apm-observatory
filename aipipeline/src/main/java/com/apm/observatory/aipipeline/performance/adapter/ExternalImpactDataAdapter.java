package com.apm.observatory.aipipeline.performance.adapter;

import com.apm.observatory.aipipeline.performance.repository.MetricsRepository;
import com.apm.observatory.aipipeline.performance.repository.SpanRepository;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ExternalImpactDataAdapter implements ExternalImpactDataPort {

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
    public List<ExternalSpanSnapshot> getRecentExternalSpans(String appName, Instant start, Instant end) {
        return spanRepository.findByAppNameAndSpanTypeAndStartTimeBetween(appName, "EXTERNAL", start, end)
                .stream()
                .map(e -> new ExternalSpanSnapshot(
                        e.getSpanId(),
                        e.getAppName(),
                        e.getExternalHost(),
                        e.getDurationMs(),
                        e.getStartTime()
                ))
                .toList();
    }

    @Override
    public Double getBaselineExternalSpanAvg(String appName, Instant start, Instant end) {
        return Optional.ofNullable(
                        spanRepository.findAvgDurationMs(appName, "EXTERNAL", start, end))
                .orElse(0.0);
    }

}