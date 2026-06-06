package com.apm.observatory.agent.sender;

import com.apm.observatory.agent.queue.DataType;
import com.apm.common.proto.MonitoringProto;
import com.apm.common.proto.MonitoringProto.LogBatch;
import com.apm.common.proto.MonitoringProto.MetricsBatch;
import com.apm.common.proto.MonitoringProto.SpanBatch;
import com.apm.common.proto.MonitoringServiceGrpc;
import com.apm.observatory.agent.config.AgentConfig;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * gRPC 비동기 전송 어댑터.
 *
 * <p>{@link DataSender} 전략 인터페이스의 gRPC + Protobuf 구현체. 전송 방식을
 * HTTP, Kafka 등으로 교체할 때 이 클래스만 교체하면 된다.
 *
 * <p>AsyncStub을 사용해 worker 스레드가 RPC 응답을 기다리지 않는다. BlockingStub을
 * 쓰면 worker 한 스레드가 RPC 한 건의 응답을 받을 때까지 기다려야 하므로, 단위
 * 시간당 처리량이 RPC 응답 시간에 따라 정해진다. AsyncStub은 호출 즉시 반환하고
 * 응답 처리는 gRPC 내부 스레드(grpc-default-executor)에서 콜백으로 처리된다.
 *
 * <p>비동기 전송은 호출 즉시 반환하므로 전송 속도를 스스로 제한하지 못한다.
 * {@link Semaphore}로 동시 inflight RPC 수에 상한을 두며, permit 수는
 * {@link AgentConfig#INFLIGHT_LIMIT}개다. worker 스레드는 RPC를 보내기 전 permit을
 * 하나 acquire하고, RPC 응답 콜백이 도착하면 permit을 하나 release한다. inflight RPC가
 * 상한까지 차서 남은 permit이 없으면 worker는 acquire에서 막혀 다음 전송을 시작하지
 * 못한다. worker가 멈추면 큐에서 데이터가 빠져나가지 못해 전송이 느려진다. 전송이
 * 느려지면 큐가 차오르고 인입 속도까지 제한되는데, 이 연쇄가 백프레셔다.
 *
 * <p>RPC가 실패해도 재시도하지 않는다. 재시도가 몰리면 retry storm으로 게이트웨이
 * 장애가 더 커질 수 있기 때문이다. 유실은 후단의 Redis Streams + AOF가 막는다.
 * 실패하면 콜백에서 카운터를 올리고 로그를 남기는 선에서 끝낸다.
 *
 * <p>shutdown은 두 단계를 거친다. 첫째, inflight RPC의 permit이 모두 release될
 * 때까지 deadline 안에서 기다린다. 둘째, 채널을 종료한 뒤 채널의 awaitTermination이
 * 끝날 때까지 deadline 안에서 기다린다. 두 단계 모두 deadline으로
 * {@link AgentConfig#SHUTDOWN_TIMEOUT_SEC}를 쓴다.
 */
public class GrpcSenderImpl implements DataSender {

    private final ManagedChannel channel;
    private final MonitoringServiceGrpc.MonitoringServiceStub asyncStub;
    private final Semaphore inflightPermits;
    private final AtomicLong sentCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    /**
     * 채널을 주입받아 AsyncStub과 inflight Semaphore를 구성한다.
     *
     * <p>채널 생성 책임은 AgentMain(Composition Root)이 지고, 이 클래스는 전송만
     * 맡는다. API Key는 인터셉터를 걸어 모든 RPC 호출에 메타데이터로 자동 첨부한다.
     */
    public GrpcSenderImpl(ManagedChannel channel) {
        this.channel = channel;
        this.inflightPermits = new Semaphore(AgentConfig.INFLIGHT_LIMIT);

        Metadata metadata = new Metadata();
        Metadata.Key<String> apiKeyHeader = Metadata.Key.of(
                AgentConfig.API_KEY_HEADER,
                Metadata.ASCII_STRING_MARSHALLER
        );
        metadata.put(apiKeyHeader, AgentConfig.API_KEY);

        this.asyncStub = MonitoringServiceGrpc.newStub(channel)
                .withInterceptors(
                        MetadataUtils.newAttachHeadersInterceptor(metadata)
                );
    }

    /**
     * 메트릭 배치를 비동기 전송한다. 빈 배치는 RPC 호출 자체를 건너뛴다.
     */
    @Override
    public void sendMetrics(MetricsBatch batch) {
        if (batch.getItemsCount() == 0) {
            return;
        }
        if (!acquirePermit()) {
            return;
        }
        asyncStub.sendMetrics(batch, createObserver(DataType.METRICS));
    }

    /**
     * span 배치를 비동기 전송한다. 빈 배치는 RPC 호출 자체를 건너뛴다.
     */
    @Override
    public void sendSpan(SpanBatch batch) {
        if (batch.getItemsCount() == 0) {
            return;
        }
        if (!acquirePermit()) {
            return;
        }
        asyncStub.sendSpan(batch, createObserver(DataType.SPAN));
    }

    /**
     * 로그 배치를 비동기 전송한다. 빈 배치는 RPC 호출 자체를 건너뛴다.
     */
    @Override
    public void sendLog(LogBatch batch) {
        if (batch.getItemsCount() == 0) {
            return;
        }
        if (!acquirePermit()) {
            return;
        }
        asyncStub.sendLog(batch, createObserver(DataType.LOG));
    }

    /**
     * inflight Semaphore에서 permit 한 개를 잡는다. 인터럽트되면 interrupt 플래그를
     * 복원하고 false를 반환해 호출 측이 RPC 호출 자체를 건너뛰도록 한다.
     *
     * <p>인터럽트는 shutdown 시 QueueWorker.stop()에서 executor.shutdownNow()가
     * worker 스레드에 발생시킨다.
     *
     * @return permit을 잡았으면 true, 인터럽트됐으면 false
     */
    private boolean acquirePermit() {
        try {
            inflightPermits.acquire();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 3종 send 메서드가 공유하는 응답 콜백을 만든다.
     *
     * <p>onCompleted는 permit을 release하고 sentCount를 올린다. onError는 permit을
     * release하고 errorCount를 올린 뒤 실패 로그를 남긴다. 재시도는 하지 않는다.
     *
     * <p>콜백을 실행하는 스레드는 gRPC 내부의 grpc-default-executor로 worker와 다른
     * 스레드다. 콜백 안에서 sleep을 걸거나 무거운 작업을 하면 다른 RPC 콜백이 밀리므로,
     * release와 카운터 갱신만 한다.
     *
     * @param type 실패 로그 식별용 데이터 타입
     */
    private StreamObserver<MonitoringProto.Response> createObserver(DataType type) {
        return new StreamObserver<MonitoringProto.Response>() {
            @Override
            public void onNext(MonitoringProto.Response value) {
            }

            @Override
            public void onCompleted() {
                inflightPermits.release();
                sentCount.incrementAndGet();
            }

            @Override
            public void onError(Throwable t) {
                inflightPermits.release();
                errorCount.incrementAndGet();
                System.err.println("[GrpcSender] " + type + " 전송 실패: " + t.getMessage());
            }
        };
    }

    /**
     * inflight를 drain하고 채널을 종료한다.
     *
     * <p>tryAcquire로 INFLIGHT_LIMIT 개의 permit을 deadline 안에 모두 회수한다.
     * permit을 모두 회수했다면 inflight RPC의 콜백이 전부 도착해 결과가 확정된 것이다.
     * deadline을 넘기면 아직 끝나지 않은 inflight 건수를 로그에 남기고 채널을 강제
     * 종료해 남은 RPC를 cancel한다.
     *
     * <p>마지막으로 sent/error/inflight 잔여 카운터를 종료 로그에 남긴다.
     */
    @Override
    public void shutdown() {
        try {
            boolean drained = inflightPermits.tryAcquire(
                    AgentConfig.INFLIGHT_LIMIT,
                    AgentConfig.SHUTDOWN_TIMEOUT_SEC,
                    TimeUnit.SECONDS
            );
            if (!drained) {
                System.err.println("[GrpcSender] inflight RPC " +
                        (AgentConfig.INFLIGHT_LIMIT - inflightPermits.availablePermits()) +
                        "건 미완료. 강제 종료 진행");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        channel.shutdown();
        try {
            if (!channel.awaitTermination(AgentConfig.SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("[GrpcSender] 종료 — sent=" + sentCount.get() +
                ", error=" + errorCount.get() +
                ", inflight 남음=" + (AgentConfig.INFLIGHT_LIMIT - inflightPermits.availablePermits()));
    }

    /** 누적 전송 성공 수. */
    public long getSentCount() {
        return sentCount.get();
    }

    /** 누적 전송 실패 수. */
    public long getErrorCount() {
        return errorCount.get();
    }

}