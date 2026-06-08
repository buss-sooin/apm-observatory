package com.apm.observatory.aipipeline.scheduler;

import com.apm.observatory.aipipeline.ai.service.OllamaConnectionManager;
import com.apm.observatory.aipipeline.context.PerformanceContextManager;
import com.apm.observatory.aipipeline.threshold.port.ThresholdConfigPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 성능 분석 파이프라인의 주기 진입점.
 *
 * <p>설정된 주기마다 깨어나 모니터링 대상 앱 전체를 순회하며 분석을
 * {@link PerformanceContextManager}에 위임한다. 앱마다 예외를 잡아
 * 기록하므로, 한 앱의 분석이 실패해도 나머지 앱 처리가 이어진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceMonitoringScheduler {

    private final PerformanceContextManager contextManager;
    private final ThresholdConfigPort thresholdConfigPort;
    private final OllamaConnectionManager ollamaConnectionManager;

    /**
     * 주기 실행. Ollama 연결이 없으면 분석을 건너뛰고, 연결돼 있으면
     * threshold에 등록된 앱마다 분석을 수행한다.
     */
    @Scheduled(
            fixedRateString = "#{${aipipeline.scheduler.interval-minutes} * 60000}",
            initialDelayString = "#{${aipipeline.scheduler.interval-minutes} * 60000}"
    )
    public void run() {
        // 연결 불가 시 시도 자체를 하지 않는다(실패를 raw_responses에 기록하지 않음)
        if (!ollamaConnectionManager.isConnected()) {
            log.warn("Ollama 연결 안 됨 — 분석 스킵 (재연결 대기 중)");
            return;
        }

        log.info("PerformanceMonitoringScheduler 실행");

        thresholdConfigPort.findAll()
                .forEach(threshold -> {
                    try {
                        contextManager.process(threshold.appName());
                    } catch (Exception e) {
                        log.error("분석 실패 app={} error={}", threshold.appName(), e.getMessage(), e);
                    }
                });
    }

}