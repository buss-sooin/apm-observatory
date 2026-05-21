package com.apm.observatory.collectorserver.processor;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 한 트레이스로 모인 span 집합을 저장 가능한 계층 구조로 조립한다.
 *
 * <p>collectorserver의 process 흐름(받은 데이터 → 가공 → 저장)에서 "가공"에 해당하는
 * 책임을 맡는다. process가 버퍼에 trace_id로 모은 span들을 넘기면, 이 클래스가
 * 부모-자식 관계를 식별하고 파생 span(INTERNAL)을 만들어 계층을 실체화한다.
 *
 * <p>이 가공은 단순 형변환이 아니라 계산이 들어간 별도 절차이고, 산식이 한 번 더
 * 바뀔 것이 예정되어 있어(트리 기반 전환, 이후 불완전 판정 연동) process 흐름에서
 * 분리했다. process는 이 클래스를 호출만 하여 결합을 느슨하게 두고, 가공에 필요한
 * 역할은 이 클래스 안에 모아 응집을 높인다.
 */
@Component
public class TraceAssembler {

    /**
     * INTERNAL span의 duration을 계산한다.
     *
     * <p>INTERNAL은 후킹으로 측정되는 span이 아니라, ROOT 전체 시간 중 측정된 자식
     * 호출이 아닌 순수 내부 처리 시간을 트리 관계로부터 도출한 파생값이다.
     *
     * <p>산식: {@code max(0, root.durationMs - Σ directChildren.durationMs)}
     *
     * <p>자식 합은 단순 산술 합이다. 타깃 앱을 동기 Spring MVC 경로로 한정해
     * 자식 호출이 시간상 겹치지 않으므로 합집합과 결과가 같다. 음수는 측정 오차로
     * 보고 0으로 막는다. 자식 판별은 span_type 문자열이 아니라 트리 관계
     * (parentSpanId == root.spanId)로만 하므로, 타입을 모르는 자식도 차감에 포함된다.
     *
     * @param root           부모가 없는 ROOT span
     * @param directChildren ROOT의 직속 자식 span 목록(parentSpanId == root.spanId)
     * @return INTERNAL duration(ms), 0 이상
     */
    public long calculateInternalDuration(Span root, List<Span> directChildren) {
        long childrenSum = directChildren.stream()
                .mapToLong(Span::durationMs)
                .sum();
        return Math.max(0L, root.durationMs() - childrenSum);
    }

}