package com.apm.observatory.collectorserver.consumer;

import com.apm.observatory.collectorserver.processor.SpanProcessor;
import com.apm.observatory.collectorserver.config.CollectorConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SpanConsumer extends AbstractStreamConsumer {

    private final SpanProcessor spanProcessor;

    public SpanConsumer(StringRedisTemplate redisTemplate,
                        SpanProcessor spanProcessor) {
        super(redisTemplate);
        this.spanProcessor = spanProcessor;
    }

    @Override
    protected String streamKey() {
        return CollectorConfig.STREAM_SPANS;
    }

    @Override
    protected String deadLetterStreamKey() {
        return CollectorConfig.STREAM_SPANS_DEAD;
    }

    @Override
    protected String logPrefix() {
        return "[SpanConsumer]";
    }

    @Override
    protected void processMessages(List<Map<String, String>> messages) {
        spanProcessor.process(messages);
    }

    // SpanConsumer만 override — TraceID 기준 30초 타임아웃 처리
    // 나머지 Consumer는 AbstractStreamConsumer의 no-op 사용
    @Override
    public void flushExpired() {
        spanProcessor.flushExpired();
    }

}