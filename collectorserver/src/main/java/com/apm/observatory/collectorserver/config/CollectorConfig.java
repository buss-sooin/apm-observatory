package com.apm.observatory.collectorserver.config;

/**
 * collectorserver가 Redis Streams를 소비하고 DLQ로 격리하며 trace 종료를 판정하는 데
 * 쓰는 상수 모음. 인스턴스화하지 않는 final 클래스다.
 */
public final class CollectorConfig {

    private CollectorConfig() {}

    /** 정상 수집 대상 Redis Stream 키. agent가 gateway를 거쳐 적재한다. */
    public static final String STREAM_METRICS = "stream:metrics";
    public static final String STREAM_SPANS   = "stream:spans";
    public static final String STREAM_LOGS    = "stream:logs";

    /**
     * 재시도 초과 메시지를 옮기는 DLQ Stream 키. 정상 Stream과 출처별로 1:1 대응시켜,
     * 어느 출처에서 실패가 났는지 운영자가 식별할 수 있게 분리 보관한다.
     */
    public static final String STREAM_METRICS_DEAD = "stream:metrics:dead";
    public static final String STREAM_SPANS_DEAD   = "stream:spans:dead";
    public static final String STREAM_LOGS_DEAD    = "stream:logs:dead";

    /**
     * 정상 Stream의 Consumer Group 이름. 같은 그룹의 소비자들이 메시지를 나눠 처리하는
     * Redis Streams Consumer Group 구조를 쓴다.
     */
    public static final String GROUP_NAME = "collector-group";

    /** DLQ Stream의 Consumer Group 이름. 정상 Group과 분리해 DLQ 메시지를 독립 추적한다. */
    public static final String DLQ_GROUP_NAME = "dlq-group";

    /** 같은 Consumer Group 안에서 이 소비자를 식별하는 이름. */
    public static final String CONSUMER_NAME = "collector-1";

    /** 한 번의 폴링에서 읽을 최대 메시지 수. */
    public static final int BATCH_SIZE = 100;

    /** 폴링 시 큐가 비었을 때 블로킹 대기하는 시간(ms). */
    public static final long POLL_TIMEOUT_MS = 1000L;

    /**
     * trace 종료 판정의 idle 임계(ms). 한 trace의 마지막 span 도착 후 이 시간 동안
     * 추가 도착이 없으면 종료로 판정한다.
     *
     * <p>값 산출 근거는 read-timeout(3초) + consume polling 지연 최악(5초) + 안전 마진이다.
     * 외부 호출 read-timeout으로 늦게 만들어진 EXTERNAL span이 collectorserver buffer에
     * 도착하기까지의 지연을 흡수해야, 그 span을 같은 trace로 묶어 저장한다.
     */
    public static final long IDLE_THRESHOLD_MS = 10_000L;

    /**
     * trace 최대수명 상한(ms). trace 생성 후 이 시간을 넘으면 idle 조건과 무관하게
     * 강제 저장한다.
     *
     * <p>메모리 누수 방어다. 정상 트래픽은 {@link #IDLE_THRESHOLD_MS}로 먼저
     * 끊기고, 이 임계는 끝없이 span이 들어오는 비정상 케이스에서만 발동한다. 값은
     * AWS ALB 기본 idle timeout 60초를 따른다.
     */
    public static final long MAX_LIFETIME_MS = 60_000L;

    /**
     * PEL 재시도 최대 횟수. 초과한 메시지는 DLQ Stream으로 옮기고 정상 PEL에서
     * ACK해 제거한다.
     */
    public static final long MAX_RETRY_COUNT = 3L;
}
