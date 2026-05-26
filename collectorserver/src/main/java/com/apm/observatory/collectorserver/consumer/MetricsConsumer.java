package com.apm.observatory.collectorserver.consumer;

import com.apm.observatory.collectorserver.config.CollectorConfig;
import com.apm.observatory.collectorserver.processor.MetricsProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Redis Stream {@code stream:metrics}에서 메트릭 메시지를 받아 MetricsProcessor로
 * 저장하는 Consumer.
 *
 * <p>도착 즉시 saveAll까지 끝나는 처리 모델로, processMessages 안에서
 * 저장과 acknowledge를 모두 수행한다.
 */
@Slf4j
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
    protected void processMessages(List<MapRecord<String, Object, Object>> records) {
        List<Map<String, String>> messages = toStringMaps(records);
        metricsProcessor.process(messages);
        acknowledge(records.stream().map(MapRecord::getId).toList());
        log.info("{} {}건 처리 완료 → DB 저장", logPrefix(), records.size());
    }

}