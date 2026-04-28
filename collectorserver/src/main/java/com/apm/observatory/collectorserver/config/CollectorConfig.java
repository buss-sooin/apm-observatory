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

    // Span 수집 대기 타임아웃 (ms) — 30초
    public static final long SPAN_TIMEOUT_MS = 30_000L;

    // PEL 재시도 최대 횟수
    // 초과 시 DLQ Stream으로 이동 + 정상 PEL에서 ACK 제거
    // 재시도 한계를 넘어서면 조용히 넘어가는데, 알림이 있어야 운영자가 인지할 수 있을 것 같음
    public static final long MAX_RETRY_COUNT = 3L;
}