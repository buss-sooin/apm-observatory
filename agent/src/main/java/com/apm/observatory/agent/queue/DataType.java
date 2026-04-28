package com.apm.observatory.agent.queue;

// 큐에 담기는 데이터 타입 구분
// QueueItem 내부 enum에서 분리한 이유:
//   GrpcSenderImpl의 sendWithRetry()에서도 타입 로깅에 사용
//   여러 클래스에서 공유하는 공통 타입이라 독립 클래스가 적합
public enum DataType {
    METRICS,
    SPAN,
    LOG
}