package com.apm.observatory.gateway.service;

import com.apm.common.proto.MonitoringProto;
import com.apm.common.proto.MonitoringServiceGrpc;
import com.apm.observatory.gateway.redis.RedisStreamPublisher;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * gRPC 서비스 구현체의 세 가지 역할.
 * <ol>
 *   <li>유효성 검증 (빈 배치 차단)</li>
 *   <li>Redis Streams 라우팅 ({@link RedisStreamPublisher}에 위임)</li>
 *   <li>자체 메트릭 (수신 건수·에러 건수)</li>
 * </ol>
 */
public class MonitoringServiceImpl extends MonitoringServiceGrpc.MonitoringServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(MonitoringServiceImpl.class);

    private final RedisStreamPublisher publisher;

    // 여러 gRPC 스레드가 동시에 호출하므로 AtomicLong으로 센다
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

    /**
     * 배치 타입별 공통 처리. 유효성을 검증한 뒤 publisher에 발행을 위임하고,
     * 수신 건수와 에러 건수를 센 다음 응답한다.
     *
     * @param publishAction 타입별 publisher 발행 동작
     */
    private void handle(int itemCount,
                        Runnable publishAction,
                        int countToAdd,
                        String batchType,
                        StreamObserver<MonitoringProto.Response> responseObserver) {
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

    /** 응답을 전송한다. onNext와 onCompleted를 항상 쌍으로 호출한다. */
    private void respond(StreamObserver<MonitoringProto.Response> responseObserver,
                         boolean success,
                         String message) {
        responseObserver.onNext(MonitoringProto.Response.newBuilder()
                .setSuccess(success)
                .setMessage(message)
                .build());
        responseObserver.onCompleted();
    }

    // GatewayServer의 메트릭 로깅 스케줄러가 주기적으로 호출한다
    public long getReceivedCount() { return receivedCount.get(); }
    public long getErrorCount() { return errorCount.get(); }

}
