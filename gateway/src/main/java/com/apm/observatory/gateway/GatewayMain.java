package com.apm.observatory.gateway;

import com.apm.observatory.gateway.server.GatewayServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayMain {

    private static final Logger log = LoggerFactory.getLogger(GatewayMain.class);

    public static void main(String[] args) throws Exception {
        GatewayServer server = new GatewayServer();

        // JVM Shutdown Hook 등록
        // Ctrl+C, System.exit() 시 graceful shutdown
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