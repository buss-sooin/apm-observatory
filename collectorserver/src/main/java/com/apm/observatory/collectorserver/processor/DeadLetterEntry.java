package com.apm.observatory.collectorserver.processor;

import java.util.List;
import java.util.Map;

import org.springframework.data.redis.connection.stream.RecordId;

/**
 * DLQ로 이동할 한 trace의 묶음.
 *
 * <p>spans는 DLQ Stream에 옮길 원본 span 데이터로, 한 trace에 묶여 있던 spans 그대로다.
 * recordIds는 같은 trace에 묶여 있던 Redis Stream 메시지 식별자 모음이다.
 *
 * <p>reason은 DLQ 이동 사유 값이다. DLQ Stream에 옮길 때 각 span의 dlq_reason 컬럼에
 * 같은 값으로 들어가 운영자가 어떤 이유로 정상 처리에서 빠졌는지 식별할 수 있게 한다.
 *
 * <p>현재 사용하는 reason 값:
 * <ul>
 *   <li>{@code "SAVE_FAILED: <예외 메시지>"} — saveAll 호출이 예외로 실패한 경우</li>
 *   <li>{@code "ROOT_MISSING"} — 종료 판정 시점에 ROOT span이 buffer에 도착하지 않은 경우</li>
 * </ul>
 */
public record DeadLetterEntry(
        List<RecordId> recordIds,
        List<Map<String, String>> spans,
        String reason
) {
}
