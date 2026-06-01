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
 * <p>AsyncStub을 사용해 worker 스레드가 RPC 응답을 기다리지 않는다. BlockingStub
 * 구조에서는 worker 한 스레드가 RPC 한 건의 응답 시간만큼 점유돼 단위 시간당
 * 처리 건수의 천장이 RPC 응답 시간에 묶였다. AsyncStub은 호출 즉시 반환하고
 * 응답 처리는 gRPC 내부 스레드(grpc-default-executor)에서 콜백으로 일어난다.
 *
 * <p>비동기 발사에 자연 제동이 없으므로 {@link Semaphore}로 동시 발사 가능한
 * RPC 수에 상한을 둔다. 호출 측은 acquire에서 대기하고(가득이면 worker 점유 =
 * 백프레셔), 콜백 측은 release한다. permit 수는 {@link AgentConfig#INFLIGHT_LIMIT}.
 *
 * <p>RPC 실패 시 재시도하지 않는다. retry storm으로 게이트웨이 장애가 확산되는
 * 자리를 만들지 않고, 유실 방지는 후단 Redis Streams + AOF가 담당한다. 콜백 측에서
 * 실패는 카운터에 기록하고 로그를 남기는 데 그친다.
 *
 * <p>shutdown은 두 단계를 거친다. 첫째, inflight 모두 release될 때까지 deadline
 * 안에 대기. 둘째, 채널 종료 후 채널의 awaitTermination을 deadline까지 기다림.
 * 두 단계 모두 {@link AgentConfig#SHUTDOWN_TIMEOUT_SEC}를 deadline으로 쓴다.
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
     * <p>채널 생성 책임은 AgentMain(Composition Root)이다. 이 클래스는 전송 책임만
     * 보유한다. API Key는 모든 RPC 호출에 메타데이터로 자동 첨부되도록 인터셉터를
     * 건다.
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
     * <p>onCompleted는 permit을 반환하고 sentCount를 증가시킨다. onError는 permit
     * 반환과 errorCount 증가, 실패 로그를 남긴다. 재시도 동작은 없다.
     *
     * <p>콜백 실행 스레드는 gRPC 내부의 grpc-default-executor이며 worker와 다른
     * 스레드다. 콜백 안에서 동기 sleep이나 무거운 처리를 두면 다른 RPC 콜백이
     * 줄을 서므로 release와 카운터 갱신 외에는 두지 않는다.
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
     * <p>tryAcquire로 INFLIGHT_LIMIT만큼의 permit을 deadline 안에 모두 회수
     * 시도한다. 회수 성공 = 모든 콜백 도달 = 모든 RPC 결과 확정. deadline을 넘으면
     * 미완료 inflight 건수를 로그에 남기고 채널을 강제 종료해 남은 RPC를 cancel
     * 시킨다.
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