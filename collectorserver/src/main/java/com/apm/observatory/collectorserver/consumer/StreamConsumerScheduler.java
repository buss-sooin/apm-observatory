package com.apm.observatory.collectorserver.consumer;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StreamConsumerScheduler {

    private final StreamConsumerProvider provider;

    public StreamConsumerScheduler(StreamConsumerProvider provider) {
        this.provider = provider;
    }

    // 5초마다 전체 Consumer 폴링
    // fixedDelay: 이전 실행 완료 후 5초 대기 — 처리 중 중복 실행 방지
    @Scheduled(fixedDelay = 5000)
    public void consume() {
        provider.getAll().forEach(AbstractStreamConsumer::consume);
    }

    // 30초마다 타임아웃된 TraceID 처리
    // SpanConsumer만 실제 동작, 나머지는 no-op
    @Scheduled(fixedDelay = 30000)
    public void flushExpired() {
        provider.getAll().forEach(AbstractStreamConsumer::flushExpired);
    }

    // 1분마다 PEL 재처리
    // 5분 이상 ACK 안 된 메시지 복구
    @Scheduled(fixedDelay = 60000)
    public void retryPending() {
        provider.getAll().forEach(AbstractStreamConsumer::retryPending);
    }

}