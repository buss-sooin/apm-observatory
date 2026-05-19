package com.apm.observatory.collectorserver.processor.adapter;

/**
 * span의 분류. collectorserver 단일 모듈 안에서만 참조되는 타입이다.
 *
 * <p>모듈 경계(agent→collectorserver gRPC, collectorserver→DB)는 문자열로
 * span_type을 주고받는다. 이 enum은 그 경계 밖으로 나가지 않고, collectorserver가
 * span에 의미를 부여·판단하는 도메인 구간 안에서만 산다. 경계에서 문자열↔enum
 * 변환만 수행한다.
 *
 * <ul>
 *   <li>{@code ROOT} — 요청 전체. 부모 없는 트리 최상위(ServletAdvice 후킹)</li>
 *   <li>{@code INTERNAL} — 순수 내부 처리 구간. 후킹이 아니라 트리 조립 시 파생</li>
 *   <li>{@code DB} — DB 호출(PreparedStatement 후킹)</li>
 *   <li>{@code EXTERNAL} — 외부 호출(RestClient 후킹)</li>
 *   <li>{@code UNKNOWN} — 필드 유무로도 리터럴로도 분간 못 한 span</li>
 * </ul>
 */
public enum SpanType {
    ROOT,
    INTERNAL,
    DB,
    EXTERNAL,
    UNKNOWN
}