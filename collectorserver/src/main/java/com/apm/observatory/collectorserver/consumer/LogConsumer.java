package com.apm.observatory.collectorserver.consumer;

import com.apm.observatory.collectorserver.config.CollectorConfig;
import com.apm.observatory.collectorserver.processor.LogProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

/**
 * Redis Stream {@code stream:logs}에서 로그 메시지를 받아 LogProcessor로 저장하는 Consumer.
 *
 * <p>도착 즉시 saveAll까지 끝나는 처리 모델로, processMessages 안에서
 * 저장과 acknowledge를 모두 수행한다.
 */
@Slf4j
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
    protected void processMessages(List<MapRecord<String, Object, Object>> records) {
        List<Map<String, String>> messages = toStringMaps(records);
        logProcessor.process(messages);
        acknowledge(records.stream().map(MapRecord::getId).toList());
        log.info("{} {}건 처리 완료 → DB 저장", logPrefix(), records.size());
    }

}