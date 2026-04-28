package com.apm.observatory.aipipeline.scheduler;

import com.apm.observatory.aipipeline.ai.service.OllamaConnectionManager;
import com.apm.observatory.aipipeline.context.PerformanceContextManager;
import com.apm.observatory.aipipeline.threshold.port.ThresholdConfigPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceMonitoringScheduler {

    private final PerformanceContextManager contextManager;
    private final ThresholdConfigPort thresholdConfigPort;
    private final OllamaConnectionManager ollamaConnectionManager;

    @Scheduled(
            fixedRateString = "#{${aipipeline.scheduler.interval-minutes} * 60000}",
            initialDelayString = "#{${aipipeline.scheduler.interval-minutes} * 60000}"
    )
    public void run() {
        // 의도: Ollama 연결 불가 시 분석 로직 자체를 건너뜀
        // AI 호출 실패를 raw_responses에 기록하는 것이 아니라
        // 연결이 안 된 상태에서는 시도 자체를 하지 않음
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