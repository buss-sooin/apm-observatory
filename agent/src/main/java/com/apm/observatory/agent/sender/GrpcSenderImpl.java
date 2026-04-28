package com.apm.observatory.agent.sender;

import com.apm.observatory.agent.queue.DataType;
import com.apm.common.proto.MonitoringProto.LogBatch;
import com.apm.common.proto.MonitoringProto.MetricsBatch;
import com.apm.common.proto.MonitoringProto.SpanBatch;
import com.apm.common.proto.MonitoringServiceGrpc;
import com.apm.observatory.agent.config.AgentConfig;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

// DataSender 전략 구현체 — gRPC + Protobuf 전송
// 전송 방식을 HTTP, Kafka 등으로 교체 시 이 클래스만 교체
public class GrpcSenderImpl implements DataSender {

    private final ManagedChannel channel;

    // Blocking 스텁: 동기 방식 RPC 호출
    // QueueWorker가 백그라운드 스레드라 블로킹해도 타겟 앱 영향 없음
    // 전송 병렬성이 필요해지면 비동기 방식으로 바꾸는 걸 고려해봐야 할 것 같음
    private final MonitoringServiceGrpc.MonitoringServiceBlockingStub stub;

    // 채널은 AgentMain에서 생성 후 주입
    // 채널 생성 책임은 AgentMain (Composition Root)
    // GrpcSenderImpl은 전송 책임만 보유 (단일 책임 원칙)
    public GrpcSenderImpl(ManagedChannel channel) {
        this.channel = channel;

        // API Key를 모든 RPC 호출의 메타데이터에 자동으로 추가
        // Metadata.Key: gRPC 메타데이터 키 정의
        // ASCII_STRING_MARSHALLER: 문자열 직렬화 방식
        // withInterceptors: 모든 RPC 호출 전에 인터셉터 실행
        // MetadataUtils.newAttachHeadersInterceptor: 메타데이터 자동 첨부 인터셉터
        // 배치 전송 시 RPC 1번 호출 = 메타데이터 1번 전송 → 인증 1번만 발생
        Metadata metadata = new Metadata();
        Metadata.Key<String> apiKeyHeader = Metadata.Key.of(
                AgentConfig.API_KEY_HEADER,
                Metadata.ASCII_STRING_MARSHALLER
        );
        metadata.put(apiKeyHeader, AgentConfig.API_KEY);

        this.stub = MonitoringServiceGrpc.newBlockingStub(channel)
                .withInterceptors(
                        MetadataUtils.newAttachHeadersInterceptor(metadata)
                );
    }

    @Override
    public void sendMetrics(MetricsBatch batch) {
        sendWithRetry(() -> stub.sendMetrics(batch), DataType.METRICS);
    }

    @Override
    public void sendSpan(SpanBatch batch) {
        sendWithRetry(() -> stub.sendSpan(batch), DataType.SPAN);
    }

    @Override
    public void sendLog(LogBatch batch) {
        sendWithRetry(() -> stub.sendLog(batch), DataType.LOG);
    }

    // 지수 백오프 재시도
    // 1차 실패 → 1초 대기 → 재시도
    // 2차 실패 → 2초 대기 → 재시도
    // 3차 실패 → 4초 대기 → 포기 (드롭)
    // Runnable 파라미터: Metrics/Spans/Logs 3종이 동일한 재시도 로직 공유
    //                   중복 제거 목적
    // 에이전트 수가 많아지면 재시도가 한꺼번에 몰릴 수 있어서 간격을 분산시키는 방식이 필요할 것 같음
    private void sendWithRetry(Runnable rpcCall, DataType type) {
        for (int attempt = 0; attempt < AgentConfig.MAX_RETRY; attempt++) {
            try {
                rpcCall.run();
                return; // 성공 시 즉시 반환
            } catch (StatusRuntimeException e) {
                // 1L << attempt: 비트 왼쪽 시프트로 2의 거듭제곱 계산
                // attempt=0 → 1 * 1000ms
                // attempt=1 → 2 * 1000ms
                // attempt=2 → 4 * 1000ms
                long delay = AgentConfig.BASE_DELAY_MS * (1L << attempt);
                System.err.println("[GrpcSender] " + type + " 전송 실패 "
                        + (attempt + 1) + "회차, " + delay + "ms 후 재시도: "
                        + e.getStatus());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    // sleep 중 종료 신호 → 재시도 중단
                    // 어차피 종료되므로 interrupt 플래그 복원 불필요
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        System.err.println("[GrpcSender] " + type + " 최대 재시도 초과, 드롭");
    }

    @Override
    public void shutdown() {
        channel.shutdown();
    }

}