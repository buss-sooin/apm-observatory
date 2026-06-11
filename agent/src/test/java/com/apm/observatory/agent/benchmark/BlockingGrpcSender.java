package com.apm.observatory.agent.benchmark;

import com.apm.common.proto.MonitoringProto;
import com.apm.common.proto.MonitoringServiceGrpc;
import com.apm.observatory.agent.config.AgentConfig;
import com.apm.observatory.agent.sender.DataSender;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 동기 전송 비교군. {@link com.apm.observatory.agent.sender.GrpcSenderImpl}의 비동기 전송과
 * 대조하기 위한 측정 전용 {@link DataSender} 구현이다.
 *
 * <p>BlockingStub은 send 한 건이 gateway 응답을 받을 때까지 worker 스레드를 막는다. worker는
 * RPC 왕복 시간 동안 큐로 돌아오지 못하므로 단위 시간당 처리량이 왕복 시간에 묶인다.
 * GrpcSenderImpl이 inflight를 Semaphore로 제한했던 것과 달리, 동기는 worker 한 스레드가
 * 본래 한 건씩만 진행하므로 별도 제한이 필요 없다.
 *
 * <p>프로덕션 전송 경로가 아니라 측정에서만 쓰는 클래스다. 두 stub의 차이만 비교에 남기려고
 * 채널과 메타데이터 구성은 GrpcSenderImpl과 같게 맞추고 전송 방식(blocking)만 다르게 둔다.
 */
public class BlockingGrpcSender implements DataSender {

    private final ManagedChannel channel;
    private final MonitoringServiceGrpc.MonitoringServiceBlockingStub blockingStub;
    private final AtomicLong sentCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    public BlockingGrpcSender(ManagedChannel channel) {
        this.channel = channel;

        Metadata metadata = new Metadata();
        Metadata.Key<String> apiKeyHeader = Metadata.Key.of(
                AgentConfig.API_KEY_HEADER,
                Metadata.ASCII_STRING_MARSHALLER
        );
        metadata.put(apiKeyHeader, AgentConfig.API_KEY);

        this.blockingStub = MonitoringServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    @Override
    public void sendMetrics(MonitoringProto.MetricsBatch batch) {
        if (batch.getItemsCount() == 0) {
            return;
        }
        sendBlocking(() -> blockingStub.sendMetrics(batch));
    }

    @Override
    public void sendSpan(MonitoringProto.SpanBatch batch) {
        if (batch.getItemsCount() == 0) {
            return;
        }
        sendBlocking(() -> blockingStub.sendSpan(batch));
    }

    @Override
    public void sendLog(MonitoringProto.LogBatch batch) {
        if (batch.getItemsCount() == 0) {
            return;
        }
        sendBlocking(() -> blockingStub.sendLog(batch));
    }

    /**
     * 응답을 받을 때까지 막은 뒤 결과 카운터를 올린다. RPC 예외는 비동기 구현과 같이
     * 재시도하지 않고 errorCount만 올린다.
     */
    private void sendBlocking(Supplier<MonitoringProto.Response> call) {
        try {
            call.get();
            sentCount.incrementAndGet();
        } catch (Exception e) {
            errorCount.incrementAndGet();
        }
    }

    @Override
    public void shutdown() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(AgentConfig.SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
