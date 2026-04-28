package com.apm.observatory.agent.sender;

import com.apm.common.proto.MonitoringProto;

// 전략 패턴 인터페이스
// GoF 의도: 전송 알고리즘 군(gRPC, HTTP, Kafka 등)을 캡슐화하여 교체 가능하게
// QueueWorker는 이 인터페이스만 알고 전송 방식을 몰라도 됨
// 전송 방식 교체 시 구현체만 교체 (QueueWorker 무변경)
//
// 단일 책임 원칙:
//   변환 책임(QueueItem → Protobuf 배치) → QueueWorker
//   전송 책임(배치 → 게이트웨이)         → DataSender 구현체
public interface DataSender {

    void sendMetrics(MonitoringProto.MetricsBatch batch);
    void sendSpan(MonitoringProto.SpanBatch batch);
    void sendLog(MonitoringProto.LogBatch batch);

    // 채널 등 네트워크 자원 해제
    // AgentMain ShutdownHook에서 호출
    void shutdown();

}