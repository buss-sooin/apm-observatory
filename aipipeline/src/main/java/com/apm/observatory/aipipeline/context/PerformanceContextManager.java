package com.apm.observatory.aipipeline.context;

import com.apm.observatory.aipipeline.ai.port.AiAnalysisResultPort;
import com.apm.observatory.aipipeline.ai.port.ErosionSlopePort;
import com.apm.observatory.aipipeline.ai.service.OllamaAnalysisService;
import com.apm.observatory.aipipeline.analysis.calculator.AppResponseTimeCalculator;
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

/**
 * 앱별 성능 분석 한 사이클을 조립하는 오케스트레이터.
 *
 * <p>스케줄러가 앱 이름을 넘기면 threshold와 baseline 윈도우를 정하고,
 * {@link PerformanceAnalysisPipelineContext} Step Builder로 설정·baseline·
 * snapshot·이상탐지·추세전달 단계를 차례로 실행한다.
 *
 * <p>{@code activeTrends}는 앱별 누적 추세(erosion 판정용)를 들고 있다가
 * erosion 윈도우가 지나면 새 추세로 교체한다. 분석에 필요한 협력 객체는
 * {@code @PostConstruct} 시점에 {@link AnalysisDependencies} 한 묶음으로
 * 구성해 매 사이클 재사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceContextManager {

    private final PerformanceDataPort performanceDataPort;
    private final ExternalImpactDataPort externalImpactDataPort;
    private final ThresholdConfigPort thresholdConfigPort;
    private final BusinessCyclePort businessCyclePort;
    private final PerformanceCollapseEvaluator collapseEvaluator;
    private final AppResponseTimeCalculator appResponseTimeCalculator;
    private final ExternalImpactEvaluator externalImpactEvaluator;
    private final PerformanceErosionEvaluator erosionEvaluator;
    private final OllamaAnalysisService ollamaAnalysisService;
    private final AiAnalysisResultPort aiAnalysisResultPort;
    private final ErosionSlopePort erosionSlopePort;
    private final AiPipelineConfig config;

    private final ConcurrentHashMap<String, PerformanceTrend> activeTrends
            = new ConcurrentHashMap<>();

    private AnalysisDependencies dependencies;

    /**
     * 협력 객체를 {@link AnalysisDependencies}로 묶고, 모니터링 앱마다
     * 추세 상태를 초기화한다. erosion 분석 주기가 스케줄러 주기보다 크지
     * 않으면(추세를 쌓을 구간이 안 나오므로) 기동을 막는다.
     */
    @PostConstruct
    void init() {
        if (config.window().erosionMinutes() <= config.scheduler().intervalMinutes()) {
            throw new IllegalStateException(
                    "Erosion 분석 주기(" + config.window().erosionMinutes() + "분)는 " +
                            "스케줄러 주기(" + config.scheduler().intervalMinutes() + "분)보다 커야 합니다.");
        }

        this.dependencies = AnalysisDependencies.builder()
                .collapseEvaluator(collapseEvaluator)
                .appResponseTimeCalculator(appResponseTimeCalculator)
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

    /**
     * 한 앱의 분석 한 사이클. threshold를 조회하고 baseline·recent 윈도우를
     * 정한 뒤 분석 파이프라인을 실행하고, erosion 윈도우가 만료된 추세는
     * 새 추세로 교체한다.
     *
     * @param appName 분석 대상 앱 이름
     */
    public void process(String appName) {
        ThresholdConfig threshold = thresholdConfigPort.findByAppName(appName)
                .orElseThrow(() -> new IllegalArgumentException("threshold_config 없음 app=" + appName));

        activeTrends.computeIfAbsent(appName,
                k -> new PerformanceTrend(appName, Instant.now()));

        Instant now = Instant.now();
        Instant recentStart = now.minus(config.window().recentMinutes(), ChronoUnit.MINUTES);

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
                                appResponseTimeCalculator.calculateAverage(
                                        port.getRecentSpans(appName, start, end)).orElse(0.0),
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

    /**
     * baseline 비교 구간을 정한다. business_cycle이 설정돼 있고 현재 시각이
     * 그 사이클 안이면 전날 동시간대를 baseline으로 쓰고(시간대별 트래픽
     * 패턴이 뚜렷한 서비스의 오탐 방지), 그 밖이거나 설정이 없으면 최근
     * {@code baselineMinutes} 구간을 쓴다.
     *
     * @return {@code [baselineStart, baselineEnd]} 두 원소 배열
     */
    private Instant[] resolveBaselineWindow(String appName, Instant now) {
        Optional<BusinessCycle> cycle = businessCyclePort.findByAppName(appName);

        if (cycle.isPresent()) {
            LocalDateTime nowLocal = LocalDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"));
            LocalTime nowTime = nowLocal.toLocalTime();
            BusinessCycle bc = cycle.get();

            // 사이클 안 → 전날 동시간대
            if (!nowTime.isBefore(bc.cycleStart()) && !nowTime.isAfter(bc.cycleEnd())) {
                Instant yesterdayNow = now.minus(1, ChronoUnit.DAYS);
                Instant baselineEnd = yesterdayNow;
                Instant baselineStart = yesterdayNow.minus(config.window().recentMinutes(), ChronoUnit.MINUTES);
                log.debug("business_cycle 적용 — 전날 동시간대 baseline app={}", appName);
                return new Instant[]{baselineStart, baselineEnd};
            }

            // 사이클 밖 → 아래 기본 윈도우로
            log.debug("business_cycle 범위 밖 — 기본 baseline 적용 app={}", appName);
        }

        Instant baselineEnd = now;
        Instant baselineStart = now.minus(config.window().baselineMinutes(), ChronoUnit.MINUTES);
        return new Instant[]{baselineStart, baselineEnd};
    }

}