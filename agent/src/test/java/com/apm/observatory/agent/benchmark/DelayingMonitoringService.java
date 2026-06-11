package com.apm.observatory.agent.benchmark;

import com.apm.common.proto.MonitoringProto;
import com.apm.common.proto.MonitoringServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * 응답이 느린 gateway를 대신하는 측정용 in-process gRPC 서비스.
 *
 * <p>측정2는 비동기 stub이 동기 stub보다 worker 스레드를 적게 붙잡는다는 것을 본다.
 * 이 차이는 RPC 한 건의 왕복 지연이 있을 때만 드러난다. 왕복이 0에 가까우면 동기 worker도
 * 거의 안 막혀 비동기와 같아진다. in-process 서버는 같은 JVM 안이라 왕복이 수 마이크로초에
 * 그치므로, 프로덕션 gateway가 원격 전송과 Redis 발행으로 갖는 왕복 지연을 핸들러에서
 * {@code responseMillis}만큼 sleep해 대신 넣는다. 이 지연은 서버가 하는 가짜 일이 아니라
 * 이 측정 환경에 빠져 있는 왕복 시간을 채우는 값으로 둔 것이다.
 *
 * <p>sleep은 worker 스레드가 아니라 gRPC 서버 실행 스레드에서 일어난다. 서버에 동시 처리
 * 스레드를 충분히 주면 inflight RPC들의 sleep이 겹쳐, 비동기 worker가 여러 건을 동시에 띄운
 * 효과가 측정에 반영된다. 서버 스레드 풀이 좁으면 이 겹침이 막혀 비동기 우위가 실제보다
 * 작게 나오므로, 호출 측이 넉넉한 풀을 주도록 전제한다. 잠든 스레드는 코어를 쥐지 않아
 * 동시 inflight가 많아도 측정 PC의 코어를 다투지 않는다.
 *
 * <p>검증·Redis 발행·인증은 측정 변수와 무관하므로 두지 않고 항상 성공 응답만 돌려준다.
 */
public class DelayingMonitoringService extends MonitoringServiceGrpc.MonitoringServiceImplBase {

    private final long responseMillis;

    public DelayingMonitoringService(long responseMillis) {
        this.responseMillis = responseMillis;
    }

    @Override
    public void sendMetrics(MonitoringProto.MetricsBatch request,
                            StreamObserver<MonitoringProto.Response> responseObserver) {
        respondAfterDelay(responseObserver);
    }

    @Override
    public void sendSpan(MonitoringProto.SpanBatch request,
                         StreamObserver<MonitoringProto.Response> responseObserver) {
        respondAfterDelay(responseObserver);
    }

    @Override
    public void sendLog(MonitoringProto.LogBatch request,
                        StreamObserver<MonitoringProto.Response> responseObserver) {
        respondAfterDelay(responseObserver);
    }

    /**
     * {@code responseMillis}만큼 기다린 뒤 성공 응답을 보낸다. 대기 중 인터럽트되면 서버
     * 종료로 보고 오류를 돌려준다.
     */
    private void respondAfterDelay(StreamObserver<MonitoringProto.Response> responseObserver) {
        try {
            if (responseMillis > 0) {
                Thread.sleep(responseMillis);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(Status.INTERNAL
                    .withDescription("서버 대기 중 인터럽트")
                    .asRuntimeException());
            return;
        }
        responseObserver.onNext(MonitoringProto.Response.newBuilder()
                .setSuccess(true)
                .setMessage("ok")
                .build());
        responseObserver.onCompleted();
    }
}
