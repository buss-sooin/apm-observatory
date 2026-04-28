package com.apm.observatory.aipipeline.context.pipeline;

import com.apm.observatory.aipipeline.analysis.model.ErosionDataPoint;
import com.apm.observatory.aipipeline.analysis.model.SlopeRecord;
import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.context.model.AnalysisContext;
import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.MetricsSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.SpanSnapshot;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;

@Slf4j
public class TransferStep {

    private final AnalysisDependencies dependencies;
    private final AnalysisContext context;

    TransferStep(AnalysisDependencies dependencies, AnalysisContext context) {
        this.dependencies = dependencies;
        this.context = context;
    }

    public void transferToTrend(int erosionWindowMinutes, double slopeMinPositive) {
        double avgCpu = context.recentMetrics().stream()
                .mapToDouble(MetricsSnapshot::cpuUsage).average().orElse(0.0);
        double avgHeap = context.recentMetrics().stream()
                .mapToDouble(MetricsSnapshot::heapUsed).average().orElse(0.0);
        double avgSpan = context.recentSpans().stream()
                .mapToDouble(SpanSnapshot::durationMs).average().orElse(0.0);

        ErosionDataPoint point = new ErosionDataPoint(Instant.now(), avgCpu, avgHeap, avgSpan);
        context.trend().addPoint(point);

        log.debug("PerformanceTrend 포인트 이관 app={} avgCpu={} avgHeap={} avgSpan={}",
                context.appName(), avgCpu, avgHeap, avgSpan);

        if (context.trend().isExpired(Instant.now(), erosionWindowMinutes)) {
            log.info("PerformanceTrend 만료 app={} Erosion 판단 시작", context.appName());
            evaluateTrend(slopeMinPositive);
        }
    }

    private void evaluateTrend(double slopeMinPositive) {
        List<ErosionDataPoint> points = context.trend().getPoints();

        List<Double> cpuValues = points.stream()
                .mapToDouble(ErosionDataPoint::avgCpu).boxed().toList();
        List<Double> spanValues = points.stream()
                .mapToDouble(ErosionDataPoint::avgSpanDuration).boxed().toList();

        // 의도: slope를 여기서 한 번만 계산 + slopeMinPositive를 SlopeRecord에 캡슐화
        // → 전략은 SlopeRecord 하나만 받아서 판단과 저장 모두 수행
        // → 기존 TransferStep → detectTrend → toTrendStatus 파라미터 전달 제거
        SlopeRecord slopeRecord = new SlopeRecord(
                context.appName(),
                dependencies.erosionEvaluator().calculateSlope(cpuValues),
                dependencies.erosionEvaluator().calculateSlope(spanValues),
                slopeMinPositive
        );

        dependencies.trendStrategies().forEach(strategy -> {
            DetectionStatus status = strategy.detectTrend(slopeRecord, dependencies);
            if (status == DetectionStatus.DETECTED) {
                strategy.onTrendDetected(slopeRecord, context, dependencies);
            } else {
                strategy.onTrendNotDetected(slopeRecord, dependencies);
            }
        });
    }

}