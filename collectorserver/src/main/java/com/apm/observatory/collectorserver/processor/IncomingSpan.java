package com.apm.observatory.collectorserver.processor;

import java.util.Map;

import org.springframework.data.redis.connection.stream.RecordId;

/**
 * Redis Stream에서 받은 한 건의 span 메시지와 그 메시지의 식별자를 묶은 입력 타입.
 *
 * <p>raw는 span의 컬럼-값 매핑이다. trace_id, span_id, parent_span_id, span_type,
 * start_time, duration_ms 등 16개 컬럼을 담는다.
 *
 * <p>recordId는 Redis Streams가 부여한 메시지 식별자다. 한 메시지의 데이터(raw)와
 * 식별자(recordId)를 같은 record에 묶어 들고 다님으로써 두 값이 인덱스로
 * 동기화되어야 하는 위험을 제거한다.
 */
public record IncomingSpan(
        Map<String, String> raw,
        RecordId recordId
) {
}
