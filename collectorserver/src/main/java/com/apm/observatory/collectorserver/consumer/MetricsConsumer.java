package com.apm.observatory.collectorserver.consumer;

import com.apm.observatory.collectorserver.config.CollectorConfig;
import com.apm.observatory.collectorserver.processor.MetricsProcessor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MetricsConsumer extends AbstractStreamConsumer {

    private final MetricsProcessor metricsProcessor;

    public MetricsConsumer(StringRedisTemplate redisTemplate,
                           MetricsProcessor metricsProcessor) {
        super(redisTemplate);
        this.metricsProcessor = metricsProcessor;
    }

    @Override
    protected String streamKey() {
        return CollectorConfig.STREAM_METRICS;
    }

    @Override
    protected String deadLetterStreamKey() {
        return CollectorConfig.STREAM_METRICS_DEAD;
    }

    @Override
    protected String logPrefix() {
        return "[MetricsConsumer]";
    }

    @Override
    protected void processMessages(List<Map<String, String>> messages) {
        metricsProcessor.process(messages);
    }

}