package com.apm.observatory.targetappmvc.config;

import java.time.Duration;

/**
 * targetappmvc 전역 상수 모음.
 *
 * <p>다른 모듈의 {@code CollectorConfig}, {@code AgentConfig}와 동일한 패턴으로
 * 설정값을 한 곳에 모은다. yml 외부화는 채택하지 않았다. 시연 시점에 값을 바꿔야
 * 할 운영 요구가 없고, 자바 상수가 IDE 추적과 빌드 시점 검증 측면에서 더 명확하다.
 */
public final class TargetAppConfig {

    // 인스턴스화 방지 — 상수 전용 클래스
    private TargetAppConfig() {}

    // ===== RestClient 타임아웃 =====

    /**
     * 외부 HTTP 호출 connect-timeout.
     *
     * <p>TCP 연결 자체는 빠르게 성공/실패해야 정상이다. 1초를 넘으면 네트워크 또는
     * 대상 서버 자체의 문제로 본다.
     */
    public static final Duration REST_CLIENT_CONNECT_TIMEOUT = Duration.ofSeconds(1);

    /**
     * 외부 HTTP 호출 read-timeout.
     *
     * <p>외부 응답이 3초 안에 오지 않으면 끊는다. 이 끊김은 RestClientRequestAdvice가
     * 외부 호출 span을 만드는 트리거이며, collectorserver의 idle임계 산출의 기준이 된다.
     * 60초 같은 통상값은 시연 시점에서 너무 길어 외부 호출 지연이 트레이스에 어떻게
     * 반영되는지 보여주기 어렵다. 시연 가능한 짧은 값으로 3초를 선택했다.
     */
    public static final Duration REST_CLIENT_READ_TIMEOUT = Duration.ofSeconds(3);

}
