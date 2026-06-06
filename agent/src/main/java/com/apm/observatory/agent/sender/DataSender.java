package com.apm.observatory.agent.sender;

import com.apm.common.proto.MonitoringProto;

/**
 * 수집 데이터를 게이트웨이로 보내는 전략 인터페이스.
 *
 * <p>gRPC, HTTP, Kafka처럼 전송 방식이 달라져도
 * {@link com.apm.observatory.agent.worker.QueueWorker}는 이 인터페이스만 알면 되고,
 * 전송 방식을 바꿀 때는 구현체만 교체한다.
 *
 * <p>책임은 둘로 나뉜다. {@code QueueItem}을 Protobuf 배치로 바꾸는 변환은 QueueWorker가,
 * 배치를 게이트웨이로 보내는 전송은 이 인터페이스의 구현체가 맡는다.
 */
public interface DataSender {

    void sendMetrics(MonitoringProto.MetricsBatch batch);
    void sendSpan(MonitoringProto.SpanBatch batch);
    void sendLog(MonitoringProto.LogBatch batch);

    /** 채널 등 네트워크 자원을 해제한다. AgentMain의 ShutdownHook에서 호출된다. */
    void shutdown();

}