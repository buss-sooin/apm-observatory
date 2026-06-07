package com.apm.observatory.gateway.config;

/**
 * gateway 전역 설정 상수. 인스턴스화하지 않는다.
 */
public final class GatewayConfig {

    private GatewayConfig() {}

    // ===== Netty gRPC 서버 =====
    public static final int GRPC_PORT = 9090;

    // ===== API Key 인증 =====
    public static final String API_KEY = "apm-secret-key";
    public static final String API_KEY_HEADER = "api-key";

    // ===== Redis =====
    /**
     * Redis 호스트. Docker 환경에서는 REDIS_HOST 환경변수로 서비스명을 주입받고,
     * 환경변수가 없는 로컬 실행에서는 localhost를 쓴다.
     */
    public static final String REDIS_HOST = System.getenv("REDIS_HOST") != null
            ? System.getenv("REDIS_HOST")
            : "localhost";
    public static final int REDIS_PORT = 6379;

    // Redis Stream 이름
    public static final String STREAM_METRICS = "stream:metrics";
    public static final String STREAM_SPANS = "stream:spans";
    public static final String STREAM_LOGS = "stream:logs";

    // ===== 자체 메트릭 로깅 주기 =====
    /** 초당 수신 건수와 에러율을 로깅하는 주기(초). */
    public static final long METRICS_LOG_INTERVAL_SEC = 10L;

}
