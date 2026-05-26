package com.apm.observatory.collectorserver.consumer;

import com.apm.observatory.collectorserver.config.CollectorConfig;
import com.apm.observatory.collectorserver.processor.DeadLetterEntry;
import com.apm.observatory.collectorserver.processor.FlushResult;
import com.apm.observatory.collectorserver.processor.IncomingSpan;
import com.apm.observatory.collectorserver.processor.SpanProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis Stream {@code stream:spans}에서 span 메시지를 받아 SpanProcessor에 buffer 적재하고,
 * 별도 시점의 flushExpired 호출에서 종료 판정·저장·acknowledge·DLQ 이동을 수행하는 Consumer.
 *
 * <p>buffer 경유 처리 모델로 LogConsumer / MetricsConsumer와 처리 시점이 다르다.
 * processMessages는 buffer 적재만 책임지고, 실제 처리(저장 또는 DLQ 이동)는
 * 스케줄러가 호출하는 flushExpired에서 일어난다.
 *
 * <p>flushExpired는 SpanProcessor가 반환한 FlushResult를 받아 두 갈래로 분기 처리한다.
 * <ul>
 *   <li>toDeadLetter — 각 span을 addToDeadLetterStream으로 옮기고, 묶인 recordId를 acknowledge</li>
 *   <li>toAcknowledge — saveAll 성공한 trace의 recordId를 acknowledge</li>
 * </ul>
 */
@Slf4j
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

    /**
     * 도착한 records를 IncomingSpan으로 변환해 SpanProcessor의 buffer에 적재한다.
     *
     * <p>acknowledge는 호출하지 않는다. 실제 처리는 별도 시점의
     * {@link #flushExpired()}에서 수행되며, 그때 saveAll 성공 또는 DLQ 이동 후
     * acknowledge가 일어난다.
     */
    @Override
    protected void processMessages(List<MapRecord<String, Object, Object>> records) {
        List<Map<String, String>> messages = toStringMaps(records);
        List<IncomingSpan> items = buildIncomingSpans(records, messages);
        spanProcessor.process(items);
        log.info("{} span {}건 buffer 적재", logPrefix(), records.size());
    }

    /**
     * SpanProcessor가 반환한 FlushResult를 받아 DLQ 이동과 acknowledge를 수행한다.
     *
     * <p>DLQ 이동을 먼저 처리한다. 각 DeadLetterEntry의 spans를 dlq_reason 컬럼을 부착해
     * 한 건씩 DLQ Stream에 옮긴 뒤 묶여 있던 recordId를 acknowledge 한다. 그 후 정상
     * 저장된 trace의 recordId를 일괄 acknowledge 한다.
     */
    @Override
    public void flushExpired() {
        FlushResult result = spanProcessor.flushExpired();

        for (DeadLetterEntry entry : result.toDeadLetter()) {
            for (Map<String, String> span : entry.spans()) {
                Map<String, String> spanWithReason = new HashMap<>(span);
                spanWithReason.put("dlq_reason", entry.reason());
                addToDeadLetterStream(spanWithReason);
            }
            acknowledge(entry.recordIds());
        }

        acknowledge(result.toAcknowledge());

        int dlqCount = result.toDeadLetter().size();
        int ackCount = result.toAcknowledge().size();
        if (dlqCount > 0 || ackCount > 0) {
            log.info("{} flushExpired 완료 (저장 ACK {}건, DLQ trace {}개)",
                    logPrefix(), ackCount, dlqCount);
        }
    }

    /**
     * records와 변환된 messages를 같은 순서로 묶어 IncomingSpan 목록으로 만든다.
     * messages는 {@link #toStringMaps(List)}가 records와 같은 순서로 변환해 보장한다.
     */
    private List<IncomingSpan> buildIncomingSpans(List<MapRecord<String, Object, Object>> records,
                                                  List<Map<String, String>> messages) {
        List<IncomingSpan> items = new java.util.ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            items.add(new IncomingSpan(messages.get(i), records.get(i).getId()));
        }
        return items;
    }

}