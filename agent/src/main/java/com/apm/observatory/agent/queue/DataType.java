package com.apm.observatory.agent.queue;

/**
 * 큐에 담기는 데이터의 타입 구분.
 *
 * <p>{@link QueueItem} 안의 enum으로 두지 않고 독립 타입으로 분리했다. {@code QueueItem}
 * 구성뿐 아니라 전송 단계의 타입 로깅 등 여러 곳에서 공유하기 때문이다.
 */
public enum DataType {
    METRICS,
    SPAN,
    LOG
}