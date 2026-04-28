package com.apm.observatory.gateway.service;

import com.apm.common.proto.MonitoringProto;
import com.apm.common.proto.MonitoringServiceGrpc;
import com.apm.observatory.gateway.redis.RedisStreamPublisher;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

// gRPC 서비스 구현체
// 역할:
//   1. 유효성 검증 (빈 배치 등)
//   2. Redis Streams 라우팅 (RedisStreamPublisher 위임)
//   3. 자체 메트릭 (수신 건수, 에러율 로깅)
//
// 더 나아간다면 여기서 고려해야 할 것들이 있음
//   샘플링 로직 추가 (트래픽 많을 때 일정 비율만 통과)
//   데이터 압축/압축 해제
//   에이전트 버전 호환성 처리
public class MonitoringServiceImpl extends MonitoringServiceGrpc.MonitoringServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(MonitoringServiceImpl.class);

    private final RedisStreamPublisher publisher;

    // 자체 메트릭 카운터
    // 여러 gRPC 스레드가 동시에 호출 → AtomicLong으로 원자적 증가
    private final AtomicLong receivedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    public MonitoringServiceImpl(RedisStreamPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void sendMetrics(MonitoringProto.MetricsBatch request,
                            StreamObserver<MonitoringProto.Response> responseObserver) {
        handle(
                request.getItemsCount(),
                () -> publisher.publishMetrics(request),
                request.getItemsCount(),
                "MetricsBatch",
                responseObserver
        );
    }

    @Override
    public void sendSpan(MonitoringProto.SpanBatch request,
                         StreamObserver<MonitoringProto.Response> responseObserver) {
        handle(
                request.getItemsCount(),
                () -> publisher.publishSpans(request),
                request.getItemsCount(),
                "SpanBatch",
                responseObserver
        );
    }

    @Override
    public void sendLog(MonitoringProto.LogBatch request,
                        StreamObserver<MonitoringProto.Response> responseObserver) {
        handle(
                request.getItemsCount(),
                () -> publisher.publishLogs(request),
                request.getItemsCount(),
                "LogBatch",
                responseObserver
        );
    }

    // 공통 처리 로직
    // 유효성 검증 → publisher 호출 → 카운트 증가 → 응답 전송 → 예외 처리
    // Runnable: 타입별 publisher 메서드를 람다로 전달
    private void handle(int itemCount,
                        Runnable publishAction,
                        int countToAdd,
                        String batchType,
                        StreamObserver<MonitoringProto.Response> responseObserver) {
        // 유효성 검증 — 빈 배치 조기 차단
        if (itemCount == 0) {
            respond(responseObserver, false, "빈 " + batchType);
            return;
        }

        try {
            publishAction.run();
            receivedCount.addAndGet(countToAdd);
            respond(responseObserver, true, "ok");
        } catch (Exception e) {
            errorCount.incrementAndGet();
            log.error("{} 처리 실패: {}", batchType, e.getMessage());
            respond(responseObserver, false, e.getMessage());
        }
    }

    // 응답 전송 공통 처리
    // onNext + onCompleted 항상 쌍으로 호출
    private void respond(StreamObserver<MonitoringProto.Response> responseObserver,
                         boolean success,
                         String message) {
        responseObserver.onNext(MonitoringProto.Response.newBuilder()
                .setSuccess(success)
                .setMessage(message)
                .build());
        responseObserver.onCompleted();
    }

    // 자체 메트릭 조회
    // GatewayServer의 ScheduledExecutorService가 주기적으로 호출해서 로깅
    public long getReceivedCount() { return receivedCount.get(); }
    public long getErrorCount() { return errorCount.get(); }

}