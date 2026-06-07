package com.apm.observatory.collectorserver.consumer;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 수집 주기를 만들어 모든 Consumer에 전달하는 스케줄러. 폴링·종료 판정·PEL 재처리의
 * 세 주기를 {@code @Scheduled}로 발생시키고, {@link StreamConsumerProvider}가 모아둔
 * 전체 {@link AbstractStreamConsumer}에 같은 호출을 전달한다.
 *
 * <p>시점 결정(스케줄링)과 처리 로직(소비)을 분리하려고 둔 클래스다. 각 주기가 무엇을
 * 하는지는 {@link AbstractStreamConsumer}의 골격 메서드에 있고, 이 클래스는 그 메서드를
 * 언제 호출할지만 책임진다.
 *
 * <p>세 주기 모두 {@code fixedDelay}를 쓴다. 이전 실행이 끝난 뒤 지정 시간을 대기하므로
 * 한 주기의 처리가 길어져도 다음 실행과 겹치지 않는다.
 */
@Component
public class StreamConsumerScheduler {

    private final StreamConsumerProvider provider;

    public StreamConsumerScheduler(StreamConsumerProvider provider) {
        this.provider = provider;
    }

    /** 5초 주기로 전체 Consumer의 정상 폴링을 호출한다. */
    @Scheduled(fixedDelay = 5000)
    public void consume() {
        provider.getAll().forEach(AbstractStreamConsumer::consume);
    }

    /**
     * 10초 주기로 전체 Consumer의 종료 판정을 호출한다. 실제 동작은 buffer 경유 처리를
     * 하는 SpanConsumer만 수행하고, 나머지 Consumer는 no-op다.
     *
     * <p>주기 값은 {@link com.apm.observatory.collectorserver.config.CollectorConfig#IDLE_THRESHOLD_MS}(10초)와
     * 같게 유지하되 리터럴로 적는다. {@code @Scheduled}는 컴파일 시점 상수만 받아
     * 상수 필드를 직접 참조할 수 없다.
     */
    @Scheduled(fixedDelay = 10000)
    public void flushExpired() {
        provider.getAll().forEach(AbstractStreamConsumer::flushExpired);
    }

    /**
     * 1분 주기로 전체 Consumer의 PEL 재처리를 호출한다. 마지막 전달 후 5분 이상
     * ACK되지 않은 메시지를 재시도하거나 DLQ로 옮긴다.
     */
    @Scheduled(fixedDelay = 60000)
    public void retryPending() {
        provider.getAll().forEach(AbstractStreamConsumer::retryPending);
    }

}
