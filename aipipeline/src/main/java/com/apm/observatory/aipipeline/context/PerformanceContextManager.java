package com.apm.observatory.aipipeline.context;

import com.apm.observatory.aipipeline.ai.port.AiAnalysisResultPort;
import com.apm.observatory.aipipeline.ai.port.ErosionSlopePort;
import com.apm.observatory.aipipeline.ai.service.OllamaAnalysisService;
import com.apm.observatory.aipipeline.analysis.evaluator.ExternalImpactEvaluator;
import com.apm.observatory.aipipeline.analysis.evaluator.PerformanceCollapseEvaluator;
import com.apm.observatory.aipipeline.analysis.evaluator.PerformanceErosionEvaluator;
import com.apm.observatory.aipipeline.threshold.businesscycle.model.BusinessCycle;
import com.apm.observatory.aipipeline.threshold.businesscycle.port.BusinessCyclePort;
import com.apm.observatory.aipipeline.config.AiPipelineConfig;
import com.apm.observatory.aipipeline.context.model.AnalysisDependencies;
import com.apm.observatory.aipipeline.context.model.BaselineMetrics;
import com.apm.observatory.aipipeline.context.pipeline.PerformanceAnalysisPipelineContext;
import com.apm.observatory.aipipeline.context.strategy.CollapseDetectionStrategy;
import com.apm.observatory.aipipeline.context.strategy.ErosionDetectionStrategy;
import com.apm.observatory.aipipeline.context.strategy.ExternalImpactDetectionStrategy;
import com.apm.observatory.aipipeline.performance.model.PerformanceSnapshot;
import com.apm.observatory.aipipeline.performance.model.PerformanceTrend;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort;
import com.apm.observatory.aipipeline.threshold.model.ThresholdConfig;
import com.apm.observatory.aipipeline.threshold.port.ThresholdConfigPort;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceContextManager {

    private final PerformanceDataPort performanceDataPort;
    private final ExternalImpactDataPort externalImpactDataPort;
    private final ThresholdConfigPort thresholdConfigPort;
    private final BusinessCyclePort businessCyclePort;
    private final PerformanceCollapseEvaluator collapseEvaluator;
    private final ExternalImpactEvaluator externalImpactEvaluator;
    private final PerformanceErosionEvaluator erosionEvaluator;
    private final OllamaAnalysisService ollamaAnalysisService;
    private final AiAnalysisResultPort aiAnalysisResultPort;
    private final ErosionSlopePort erosionSlopePort;
    private final AiPipelineConfig config;

    private final ConcurrentHashMap<String, PerformanceTrend> activeTrends
            = new ConcurrentHashMap<>();

    private AnalysisDependencies dependencies;

    @PostConstruct
    void init() {
        if (config.window().erosionMinutes() <= config.scheduler().intervalMinutes()) {
            throw new IllegalStateException(
                    "Erosion 분석 주기(" + config.window().erosionMinutes() + "분)는 " +
                            "스케줄러 주기(" + config.scheduler().intervalMinutes() + "분)보다 커야 합니다.");
        }

        this.dependencies = AnalysisDependencies.builder()
                .collapseEvaluator(collapseEvaluator)
                .externalImpactEvaluator(externalImpactEvaluator)
                .erosionEvaluator(erosionEvaluator)
                .ollamaAnalysisService(ollamaAnalysisService)
                .aiAnalysisResultPort(aiAnalysisResultPort)
                .erosionSlopePort(erosionSlopePort)
                .detectionStrategies(List.of(
                        new CollapseDetectionStrategy(),
                        new ExternalImpactDetectionStrategy()
                ))
                .trendStrategies(List.of(
                        new ErosionDetectionStrategy()
                ))
                .build();

        thresholdConfigPort.findAll()
                .forEach(c -> activeTrends.put(
                        c.appName(),
                        new PerformanceTrend(c.appName(), Instant.now())));

        log.info("PerformanceContextManager 초기화 완료 모니터링 앱 수={}", activeTrends.size());
    }

    public void process(String appName) {
        ThresholdConfig threshold = thresholdConfigPort.findByAppName(appName)
                .orElseThrow(() -> new IllegalArgumentException("threshold_config 없음 app=" + appName));

        activeTrends.computeIfAbsent(appName,
                k -> new PerformanceTrend(appName, Instant.now()));

        Instant now = Instant.now();
        Instant recentStart = now.minus(config.window().recentMinutes(), ChronoUnit.MINUTES);

        // 의도: business_cycle 설정 있으면 전날 동시간대를 baseline으로 사용
        // 시간대별 트래픽 패턴이 뚜렷한 서비스에서 오탐 방지
        // 없으면 최근 N분 평균 사용 (기본 동작)
        Instant[] baselineWindow = resolveBaselineWindow(appName, now);
        Instant baselineStart = baselineWindow[0];
        Instant baselineEnd = baselineWindow[1];

        PerformanceAnalysisPipelineContext
                .startWith(dependencies, appName, activeTrends.get(appName))
                .configure(threshold)
                .loadBaseline(
                        (port, ext, start, end) -> new BaselineMetrics(
                                port.getBaselineCpuAvg(appName, start, end),
                                port.getBaselineHeapAvg(appName, start, end),
                                port.getBaselineSpanAvg(appName, "INTERNAL", start, end),
                                ext.getBaselineExternalSpanAvg(appName, start, end)),
                        baselineStart, baselineEnd,
                        performanceDataPort, externalImpactDataPort)
                .loadSnapshot(
                        (port, ext, start, end, baseline) -> new PerformanceSnapshot(
                                appName, start, end,
                                port.getRecentMetrics(appName, start, end),
                                port.getRecentSpans(appName, start, end),
                                ext.getRecentExternalSpans(appName, start, end),
                                baseline.baselineCpuAvg(),
                                baseline.baselineHeapAvg(),
                                baseline.baselineSpanAvg(),
                                baseline.baselineExternalAvg()),
                        recentStart, now,
                        performanceDataPort, externalImpactDataPort)
                .analyzeAnomalies()
                .transferToTrend(config.window().erosionMinutes(), threshold.slopeMinPositive());

        if (activeTrends.get(appName).isExpired(now, config.window().erosionMinutes())) {
            activeTrends.put(appName, new PerformanceTrend(appName, now));
            log.info("PerformanceTrend 교체 app={}", appName);
        }
    }

    // 의도: business_cycle 존재 여부에 따라 baseline 시간 윈도우 결정
    // business_cycle 있음 → [어제 동시각 - recentMinutes, 어제 동시각]
    // business_cycle 없음 → [now - baselineMinutes, now]
    private Instant[] resolveBaselineWindow(String appName, Instant now) {
        Optional<BusinessCycle> cycle = businessCyclePort.findByAppName(appName);

        if (cycle.isPresent()) {
            LocalDateTime nowLocal = LocalDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"));
            LocalTime nowTime = nowLocal.toLocalTime();
            BusinessCycle bc = cycle.get();

            // 비즈니스 사이클 범위 안에 있을 때만 전날 동시간대 적용
            if (!nowTime.isBefore(bc.cycleStart()) && !nowTime.isAfter(bc.cycleEnd())) {
                Instant yesterdayNow = now.minus(1, ChronoUnit.DAYS);
                Instant baselineEnd = yesterdayNow;
                Instant baselineStart = yesterdayNow.minus(config.window().recentMinutes(), ChronoUnit.MINUTES);
                log.debug("business_cycle 적용 — 전날 동시간대 baseline app={}", appName);
                return new Instant[]{baselineStart, baselineEnd};
            }

            // 비즈니스 사이클 범위 밖이면 기본값 사용
            log.debug("business_cycle 범위 밖 — 기본 baseline 적용 app={}", appName);
        }

        Instant baselineEnd = now;
        Instant baselineStart = now.minus(config.window().baselineMinutes(), ChronoUnit.MINUTES);
        return new Instant[]{baselineStart, baselineEnd};
    }

}