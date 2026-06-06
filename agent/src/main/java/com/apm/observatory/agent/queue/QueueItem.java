package com.apm.observatory.agent.queue;

/**
 * 큐에 담기는 데이터 래퍼. 큐 안에서 값이 바뀌지 않는 불변 객체라 record로 둔다.
 */
public record QueueItem(DataType type, Object data) {}