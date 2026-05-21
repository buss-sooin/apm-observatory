package com.apm.observatory.collectorserver.processor;

/**
 * 트레이스 조립에 쓰이는 span의 도메인 표현.
 *
 * <p>collectorserver가 Redis Streams에서 받은 span은 {@code Map<String,String>}
 * 형태다. 가공 로직이 그 Map 파싱을 직접 떠안으면 도메인 판단이 문자열 변환에
 * 종속되어 단독 검증이 어려워진다. 그 종속을 끊기 위해, 트레이스 조립에 필요한
 * 값만 담은 순수 데이터 타입으로 먼저 변환한 뒤 도메인 로직에 넘긴다.
 *
 * <p>spanId/parentSpanId는 트리에서 ROOT와 직속 자식을 가르는 데, durationMs는
 * 시간 차감 계산에, spanType은 조립 결과(특히 파생 INTERNAL)를 식별하는 데 쓰인다.
 * 저장 시 필요한 나머지 필드(http_*, sql_query 등)는 트리 조립 판단과 무관하므로
 * 포함하지 않는다. 그 필드들은 인프라 경계(Map↔Object[] 변환)에서만 다룬다.
 *
 * @param spanId       이 span의 식별자
 * @param parentSpanId 부모 span의 식별자. ROOT는 부모가 없어 null
 * @param spanType     이 span의 분류
 * @param durationMs   이 span의 측정 소요 시간(ms). 후킹 시점에 확정되어 전송된 값
 */
public record Span(String spanId, String parentSpanId, SpanType spanType, long durationMs) {
}