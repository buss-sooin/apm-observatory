package com.apm.observatory.collectorserver.processor;

import java.util.List;

import org.springframework.data.redis.connection.stream.RecordId;

/**
 * SpanProcessor.flushExpired의 반환 묶음. 종료 판정 통과한 trace들의 처리 결과를
 * ACK 대상과 DLQ 이동 대상 두 갈래로 나누어 담는다.
 *
 * <p>toAcknowledge는 saveAll 성공으로 정상 처리된 trace에 묶여 있던 recordId 모음이다.
 * toDeadLetter는 saveAll 영구 실패 또는 ROOT 부재로 buffer에서 제거된 trace의
 * 묶음으로, 각 항목이 한 trace 단위다.
 */
public record FlushResult(
        List<RecordId> toAcknowledge,
        List<DeadLetterEntry> toDeadLetter
) {
}
