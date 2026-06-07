package com.apm.observatory.gateway;

import com.apm.observatory.gateway.server.GatewayServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gateway 실행 진입점.
 *
 * <p>{@link GatewayServer}를 생성해 기동하고, JVM 종료 신호(Ctrl+C, {@code System.exit})가
 * 오면 graceful shutdown하도록 shutdown hook을 등록한다.
 */
public class GatewayMain {

    private static final Logger log = LoggerFactory.getLogger(GatewayMain.class);

    public static void main(String[] args) throws Exception {
        GatewayServer server = new GatewayServer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("종료 시작");
            try {
                server.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        server.start();
        server.blockUntilShutdown();
    }

}
