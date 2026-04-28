package com.apm.observatory.agent.config;

public final class AgentConfig {

    // 인스턴스화 방지 — 상수 전용 클래스
    private AgentConfig() {}

    // ===== gRPC 게이트웨이 =====
    // AgentMain에서 ManagedChannel 생성 시 사용
    // AgentComponents 빌더의 기본값으로도 사용
    // 의도: Docker 환경에서는 GATEWAY_HOST 환경변수로 서비스명 주입
    // 로컬 실행 시에는 환경변수 없으므로 localhost 기본값 사용
    public static final String GATEWAY_HOST = System.getenv("GATEWAY_HOST") != null
            ? System.getenv("GATEWAY_HOST")
            : "localhost";
    public static final int GATEWAY_PORT = 9090;

    // API Key 인증 — gRPC 메타데이터로 전송
    // 게이트웨이의 GatewayConfig.API_KEY와 동일한 값이어야 함
    // 지금은 상수로 뒀는데, 외부에서 주입할 수 있게 바꿔야 할 것 같음
    public static final String API_KEY = "apm-secret-key";
    public static final String API_KEY_HEADER = "api-key";

    // ===== DataQueue =====
    // ArrayBlockingQueue 고정 크기
    // 꽉 차면 offer() 드롭 → 타겟 앱 영향 금지 원칙
    // 수집량이 많아지면 이 크기로는 부족해질 것 같고, 메모리 외 별도 저장 방식도 필요해질 수 있음
    public static final int QUEUE_CAPACITY = 1000;

    // ===== QueueWorker =====
    // 한 번의 drainTo()에서 꺼낼 최대 건수
    // 너무 크면 전송 지연, 너무 작으면 전송 횟수 증가
    public static final int BATCH_SIZE = 100;

    // 큐가 비어있을 때 대기 시간
    // 너무 짧으면 CPU 낭비, 너무 길면 데이터 처리 지연
    public static final long IDLE_WAIT_MS = 100L;

    // ShutdownHook에서 QueueWorker 종료 대기 시간
    // 초과 시 강제 종료 (shutdownNow)
    public static final long SHUTDOWN_TIMEOUT_SEC = 5L;

    // ===== GrpcSenderImpl 재시도 =====
    // 지수 백오프: 1초 → 2초 → 4초 후 포기 (드롭)
    // 에이전트가 많아지면 재시도가 동시에 몰릴 수 있어서 분산 처리 방식이 필요할 것 같음
    public static final int MAX_RETRY = 3;
    public static final long BASE_DELAY_MS = 1000L;

    // MetricsCollector
    public static final long METRICS_INTERVAL_SEC = 5L;
    public static final String APP_NAME_PROPERTY = "apm.app.name";
    public static final String DEFAULT_APP_NAME = "unknown";

}