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

/**
 * gateway의 gRPC 서버 구성과 시작·종료를 담당한다.
 *
 * <p>{@code io.grpc.ServerBuilder.forPort}로 서버를 구성하며, 클래스패스의
 * grpc-netty-shaded 구현이 선택돼 Netty 기반 서버로 동작한다. Netty 세부 설정은
 * 직접 구성하지 않고 라이브러리 기본 구성을 쓴다.
 */
public class GatewayServer {

    private static final Logger log = LoggerFactory.getLogger(GatewayServer.class);

    private final Server server;
    private final RedisStreamPublisher publisher;
    private final MonitoringServiceImpl monitoringService;
    private final ScheduledExecutorService metricsLogger;

    public GatewayServer() {
        this.publisher = new RedisStreamPublisher();
        this.monitoringService = new MonitoringServiceImpl(publisher);

        this.server = ServerBuilder
                .forPort(GatewayConfig.GRPC_PORT)
                .addService(monitoringService)
                .intercept(new ApiKeyAuthInterceptor())
                .build();

        // 자체 메트릭(수신·에러 건수)을 주기적으로 로깅하는 스케줄러
        this.metricsLogger = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gateway-metrics-logger");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() throws IOException {
        server.start();
        log.info("시작 (포트: {})", GatewayConfig.GRPC_PORT);

        // ScheduledExecutorService는 태스크에서 예외가 나면 이후 스케줄을 조용히 취소한다.
        // try-catch로 감싸 예외가 나도 스케줄러가 계속 돌도록 한다.
        metricsLogger.scheduleAtFixedRate(() -> {
                    try {
                        log.info("메트릭 수신: {}건, 에러: {}건",
                                monitoringService.getReceivedCount(),
                                monitoringService.getErrorCount());
                    } catch (Exception e) {
                        log.warn("메트릭 로깅 실패: {}", e.getMessage());
                    }
                }, GatewayConfig.METRICS_LOG_INTERVAL_SEC,
                GatewayConfig.METRICS_LOG_INTERVAL_SEC,
                TimeUnit.SECONDS);
    }

    public void stop() throws InterruptedException {
        metricsLogger.shutdown();
        if (server != null) {
            // graceful shutdown: 진행 중인 RPC 완료를 기다린다
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
        publisher.shutdown();
        log.info("종료 완료");
    }

    /** 서버가 종료될 때까지 메인 스레드를 블로킹한다. */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

}
