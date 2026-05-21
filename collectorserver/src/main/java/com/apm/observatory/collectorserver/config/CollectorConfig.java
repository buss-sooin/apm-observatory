package com.apm.observatory.collectorserver.config;

public final class CollectorConfig {

    private CollectorConfig() {}

    // Redis Streams 키
    public static final String STREAM_METRICS = "stream:metrics";
    public static final String STREAM_SPANS   = "stream:spans";
    public static final String STREAM_LOGS    = "stream:logs";

    // DLQ(Dead Letter Queue) Stream 키
    // 재시도 초과 메시지를 출처별로 분리 보관
    // 출처 추적 + 나중에 자동 재처리 컨슈머 붙이기 용이
    public static final String STREAM_METRICS_DEAD = "stream:metrics:dead";
    public static final String STREAM_SPANS_DEAD   = "stream:spans:dead";
    public static final String STREAM_LOGS_DEAD    = "stream:logs:dead";

    // Consumer Group 이름
    // Redis Streams Consumer Group: 같은 그룹 내 여러 소비자가 메시지를 나눠서 처리
    // 지금은 소비자가 1개지만 그룹 구조를 갖춰두면 나중에 수평 확장해도 코드 변경 없이 될 것 같음
    public static final String GROUP_NAME = "collector-group";

    // DLQ Consumer Group 이름
    // 정상 Group과 분리 — 출처별 DLQ 메시지 독립 추적
    // 나중에 자동 재처리 컨슈머 붙일 때 이 Group으로 XREADGROUP
    public static final String DLQ_GROUP_NAME = "dlq-group";

    // Consumer 이름 (같은 그룹 내 식별자)
    public static final String CONSUMER_NAME = "collector-1";

    // 한 번에 읽을 최대 메시지 수
    public static final int BATCH_SIZE = 100;

    // 폴링 대기 시간 (ms) — 큐가 비었을 때 블로킹 대기
    public static final long POLL_TIMEOUT_MS = 1000L;

    // Trace 종료 판정 idle 임계 (ms)
    // 한 trace의 마지막 span 도착 후 이 시간 동안 추가 도착이 없으면 종료로 판정한다.
    // 값 산출 근거: read-timeout(3초) + consume polling 지연 최악(5초) + 안전 마진.
    // 외부 호출 read-timeout으로 늦게 만들어진 EXTERNAL span이 collectorserver buffer에
    // 도착하기까지의 지연을 흡수해야, 그 span을 같은 trace로 묶어 저장할 수 있다.
    public static final long IDLE_THRESHOLD_MS = 10_000L;

    // Trace 최대수명 상한 (ms)
    // trace 생성 후 이 시간을 넘으면 idle 조건과 무관하게 강제 저장한다.
    // 메모리 누수 방어 그물망. 정상 트래픽은 IDLE_THRESHOLD_MS로 먼저 끊기고
    // 이 임계는 비정상(끝없이 span이 들어오는 케이스)에서만 발동한다.
    // 값은 AWS ALB 기본 idle timeout 60초를 따른다.
    public static final long MAX_LIFETIME_MS = 60_000L;

    // PEL 재시도 최대 횟수
    // 초과 시 DLQ Stream으로 이동 + 정상 PEL에서 ACK 제거
    // 재시도 한계를 넘어서면 조용히 넘어가는데, 알림이 있어야 운영자가 인지할 수 있을 것 같음
    public static final long MAX_RETRY_COUNT = 3L;
}