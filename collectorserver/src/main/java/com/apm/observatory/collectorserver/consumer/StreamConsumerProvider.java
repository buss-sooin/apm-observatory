package com.apm.observatory.collectorserver.consumer;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StreamConsumerProvider {

    private final List<AbstractStreamConsumer> consumers;

    // Spring이 AbstractStreamConsumer 구현체 전부 자동 주입
    // MetricsConsumer, SpanConsumer, LogConsumer
    public StreamConsumerProvider(List<AbstractStreamConsumer> consumers) {
        this.consumers = consumers;
    }

    public List<AbstractStreamConsumer> getAll() {
        return consumers;
    }

}