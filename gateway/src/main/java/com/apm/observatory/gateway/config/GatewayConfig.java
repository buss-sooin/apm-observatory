package com.apm.observatory.gateway.config;

public final class GatewayConfig {

    private GatewayConfig() {}

    // ===== Netty gRPC 서버 =====
    public static final int GRPC_PORT = 9090;

    // ===== API Key 인증 =====
    // 지금은 하드코딩했는데, 고객사가 여럿이라면 외부에서 관리하는 방식이 필요할 것 같음
    public static final String API_KEY = "apm-secret-key";
    public static final String API_KEY_HEADER = "api-key";

    // ===== Redis =====
    // 의도: Docker 환경에서는 REDIS_HOST 환경변수로 서비스명 주입
    // 로컬 실행 시에는 환경변수 없으므로 localhost 기본값 사용
    public static final String REDIS_HOST = System.getenv("REDIS_HOST") != null
            ? System.getenv("REDIS_HOST")
            : "localhost";
    public static final int REDIS_PORT = 6379;

    // Redis Stream 이름
    public static final String STREAM_METRICS = "stream:metrics";
    public static final String STREAM_SPANS = "stream:spans";
    public static final String STREAM_LOGS = "stream:logs";

    // ===== 자체 메트릭 로깅 주기 =====
    // 초당 수신 건수, 에러율을 이 주기마다 로깅
    public static final long METRICS_LOG_INTERVAL_SEC = 10L;

}