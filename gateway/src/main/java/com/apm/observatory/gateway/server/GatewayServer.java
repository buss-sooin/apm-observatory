package com.apm.observatory.gateway.server;

import com.apm.observatory.gateway.config.GatewayConfig;
import com.apm.observatory.gateway.interceptor.ApiKeyAuthInterceptor;
import com.apm.observatory.gateway.service.MonitoringServiceImpl;
import com.apm.observatory.gateway.redis.RedisStreamPublisher;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Netty gRPC 서버 구성 및 시작/종료 담당
// NettyServerBuilder 선택 이유:
//   grpc-netty를 직접 제어해서 Netty 설정(스레드 수 등) 커스터마이징 가능
//   트래픽 규모에 따라 스레드 구성이나 메시지 크기 제한을 조정해야 할 것 같음
public class GatewayServer {

    private static final Logger log = LoggerFactory.getLogger(GatewayServer.class);

    private final Server server;
    private final RedisStreamPublisher publisher;
    private final MonitoringServiceImpl monitoringService;
    private final ScheduledExecutorService metricsLogger;

    public GatewayServer() {
        this.publisher = new RedisStreamPublisher();
        this.monitoringService = new MonitoringServiceImpl(publisher);

        // NettyServerBuilder로 gRPC 서버 구성
        // intercept(): 모든 RPC 호출 전에 인터셉터 실행
        // 인터셉터 실행 순서: 등록 역순 (마지막 등록 → 먼저 실행)
        this.server = ServerBuilder
                .forPort(GatewayConfig.GRPC_PORT)
                .addService(monitoringService)
                .intercept(new ApiKeyAuthInterceptor())
                .build();

        // 자체 메트릭 로깅 스케줄러
        // 초당 수신 건수, 에러율을 주기적으로 로깅
        // 메트릭을 지금은 로그로만 남기는데, 외부에서 수집할 수 있는 형태로 노출해야 할 것 같음
        this.metricsLogger = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gateway-metrics-logger");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() throws IOException {
        server.start();
        log.info("시작 (포트: {})", GatewayConfig.GRPC_PORT);

        // 자체 메트릭 주기적 로깅 시작
        // 주의: ScheduledExecutorService는 태스크에서 예외 발생 시 이후 스케줄을 조용히 취소함
        // try-catch로 감싸서 예외가 발생해도 스케줄러가 계속 실행되도록 보장
        metricsLogger.scheduleAtFixedRate(() -> {
                    try {
                        log.info("메트릭 수신: {}건, 에러: {}건",
                                monitoringService.getReceivedCount(),
                                monitoringService.getErrorCount());
                    } catch (Exception e) {
                        // 메트릭 로깅 실패 — 스케줄러는 계속 실행
                        log.warn("메트릭 로깅 실패: {}", e.getMessage());
                    }
                }, GatewayConfig.METRICS_LOG_INTERVAL_SEC,
                GatewayConfig.METRICS_LOG_INTERVAL_SEC,
                TimeUnit.SECONDS);
    }

    public void stop() throws InterruptedException {
        metricsLogger.shutdown();
        if (server != null) {
            // graceful shutdown: 진행 중인 RPC 완료 대기
            // 서버가 종료될 때 연결된 에이전트들도 인지할 수 있으면 좋을 것 같음
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
        publisher.shutdown();
        log.info("종료 완료");
    }

    // 서버가 종료될 때까지 메인 스레드 블로킹
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

}