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

/** 최근 지표를 추세에 누적하고, erosion 윈도우가 만료되면 추세를 판정하는 단계. */
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

    /**
     * 누적된 추세 포인트로 erosion을 판정한다. slope(cpu·span)를 여기서 한 번만
     * 계산해 {@code slopeMinPositive}와 함께 {@link SlopeRecord}로 묶으므로,
     * 추세 전략은 이 한 객체만 받아 판정과 저장을 수행한다.
     */
    private void evaluateTrend(double slopeMinPositive) {
        List<ErosionDataPoint> points = context.trend().getPoints();

        List<Double> cpuValues = points.stream()
                .mapToDouble(ErosionDataPoint::avgCpu).boxed().toList();
        List<Double> spanValues = points.stream()
                .mapToDouble(ErosionDataPoint::avgSpanDuration).boxed().toList();

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