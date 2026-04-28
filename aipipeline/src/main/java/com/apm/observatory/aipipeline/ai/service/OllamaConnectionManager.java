package com.apm.observatory.aipipeline.ai.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// 의도: Ollama 서버 연결 상태만 전담하는 컴포넌트
// OllamaAnalysisService와 분리 — 연결 상태 관리와 AI 호출 로직은 다른 책임
// isConnected()로 스케줄러가 AI 호출 전 연결 여부를 확인
@Slf4j
@Component
public class OllamaConnectionManager {

    private static final int RETRY_INTERVAL_SECONDS = 10;
    private static final int CONNECT_TIMEOUT_MS = 3000;

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final ScheduledExecutorService retryScheduler =
            Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        if (checkHealth()) {
            connected.set(true);
            log.info("Ollama 연결 확인 완료: {}", ollamaBaseUrl);
        } else {
            log.warn("Ollama 연결 실패 — 재접속 시도 중 ({}초 간격): {}", RETRY_INTERVAL_SECONDS, ollamaBaseUrl);
            scheduleRetry();
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    private void scheduleRetry() {
        retryScheduler.scheduleAtFixedRate(() -> {
            try {
                if (checkHealth()) {
                    connected.set(true);
                    log.info("Ollama 재연결 성공: {}", ollamaBaseUrl);
                    retryScheduler.shutdown();
                } else {
                    log.warn("Ollama 재접속 대기 중...");
                }
            } catch (Exception e) {
                log.warn("Ollama 재접속 시도 중 오류: {}", e.getMessage());
            }
        }, RETRY_INTERVAL_SECONDS, RETRY_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private boolean checkHealth() {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    URI.create(ollamaBaseUrl).toURL().openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

}