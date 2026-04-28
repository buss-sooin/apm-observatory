package com.apm.observatory.collectorserver.consumer;

import com.apm.observatory.collectorserver.processor.LogProcessor;
import com.apm.observatory.collectorserver.config.CollectorConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LogConsumer extends AbstractStreamConsumer {

    private final LogProcessor logProcessor;

    public LogConsumer(StringRedisTemplate redisTemplate,
                       LogProcessor logProcessor) {
        super(redisTemplate);
        this.logProcessor = logProcessor;
    }

    @Override
    protected String streamKey() {
        return CollectorConfig.STREAM_LOGS;
    }

    @Override
    protected String deadLetterStreamKey() {
        return CollectorConfig.STREAM_LOGS_DEAD;
    }

    @Override
    protected String logPrefix() {
        return "[LogConsumer]";
    }

    @Override
    protected void processMessages(List<Map<String, String>> messages) {
        logProcessor.process(messages);
    }

}