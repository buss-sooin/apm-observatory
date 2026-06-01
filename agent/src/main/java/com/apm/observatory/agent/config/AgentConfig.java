package com.apm.observatory.agent.config;

/**
 * 에이전트 전역 설정 상수 모음.
 *
 * <p>에이전트는 Spring 컨테이너 없이 동작하므로 환경 설정을 외부 주입할 자리가 없다.
 * 시스템 환경변수와 컴파일 타임 상수로 모든 설정을 표현하고, 한 자리에 모아 변경 영향
 * 범위를 좁힌다.
 *
 * <p>인스턴스화를 막기 위해 final 클래스 + private 생성자 형태로 둔다.
 */
public final class AgentConfig {

    private AgentConfig() {}

    // ===== gRPC 게이트웨이 =====

    /** 게이트웨이 호스트. 환경변수 GATEWAY_HOST가 있으면 그 값, 없으면 localhost. */
    public static final String GATEWAY_HOST = System.getenv("GATEWAY_HOST") != null
            ? System.getenv("GATEWAY_HOST")
            : "localhost";

    /** 게이트웨이 gRPC 포트. */
    public static final int GATEWAY_PORT = 9090;

    /** API Key 값. 게이트웨이 GatewayConfig.API_KEY와 동일해야 한다. */
    public static final String API_KEY = "apm-secret-key";

    /** API Key를 실어 보내는 gRPC 메타데이터 헤더 이름. */
    public static final String API_KEY_HEADER = "api-key";

    // ===== DataQueue =====

    /** MpscArrayQueue capacity. 2의 거듭제곱으로 올림되어 실제 1024개 슬롯이 잡힌다. */
    public static final int QUEUE_CAPACITY = 1000;

    // ===== QueueWorker =====

    /** 한 번의 drain에서 꺼낼 최대 건수. */
    public static final int BATCH_SIZE = 100;

    /** 큐가 비어있을 때 worker가 sleep 하는 시간. */
    public static final long IDLE_WAIT_MS = 100L;

    /** shutdown 단계별 graceful 대기 deadline. */
    public static final long SHUTDOWN_TIMEOUT_SEC = 5L;

    // ===== GrpcSenderImpl =====

    /** 동시 발사 가능한 inflight RPC 상한(Semaphore permit 수). */
    public static final int INFLIGHT_LIMIT = 50;

    // ===== MetricsCollector =====

    /** 시스템 메트릭 수집 주기. */
    public static final long METRICS_INTERVAL_SEC = 5L;

    /** -Dapm.app.name 시스템 프로퍼티 이름. */
    public static final String APP_NAME_PROPERTY = "apm.app.name";

    /** 시스템 프로퍼티가 없을 때 사용하는 기본 app_name. */
    public static final String DEFAULT_APP_NAME = "unknown";

}