package com.apm.observatory.collectorserver.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 한 트레이스로 모인 span 집합을 저장 가능한 계층 구조로 조립한다.
 *
 * <p>collectorserver의 process 흐름(받은 데이터 → 가공 → 저장)에서 "가공"에 해당하는
 * 책임을 맡는다. process가 버퍼에 trace_id로 모은 span들을 넘기면, 이 클래스가
 * 부모-자식 관계를 식별하고 파생 span(INTERNAL)을 만들어 계층을 실체화한다.
 *
 * <p>이 가공은 단순 형변환이 아니라 계산이 들어간 별도 절차여서 process 흐름에서
 * 분리했다. process는 이 클래스를 호출만 하여 결합을 느슨하게 두고, 가공에 필요한
 * 역할은 이 클래스 안에 모아 응집을 높인다.
 */
@Slf4j
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
     * <p>자식 합은 자식 duration의 단순 산술 합이다. 타깃 앱을 동기 Spring MVC 경로로
     * 한정하면 자식 호출의 시간 구간이 서로 겹치지 않아, 이 단순 합이 구간들의
     * 합집합(겹친 시간을 한 번만 센 총길이)과 같아진다. 자식 판별은 span_type 문자열이
     * 아니라 트리 관계(parentSpanId == root.spanId)로만 하므로, 타입을 모르는 자식도
     * 차감에 포함된다.
     *
     * <p>0으로 클램핑하기 직전에 log.warn을 남긴다. 깊이 3 이상의 중첩 호출이 평탄화돼
     * 저장되면 자식 구간이 서로 겹치는데, 단순 합은 겹친 시간을 중복으로 세므로
     * 합집합보다 커진다. 그러면 root 시간에서 그 합을 뺀 값이 음수가 된다. 음수는 0으로
     * 처리하지만, 단순 합이 합집합과 어긋나는 지점을 드러내는 신호라 traceId와 측정값을
     * 함께 기록한다.
     *
     * @param traceId        진단 로그 추적용 trace 식별자
     * @param root           부모가 없는 ROOT span
     * @param directChildren ROOT의 직속 자식 span 목록(parentSpanId == root.spanId)
     * @return INTERNAL duration(ms), 0 이상
     */
    public long calculateInternalDuration(String traceId, Span root, List<Span> directChildren) {
        long childrenSum = directChildren.stream()
                .mapToLong(Span::durationMs)
                .sum();
        long raw = root.durationMs() - childrenSum;

        if (raw < 0L) {
            log.warn("계산식1 음수 감지: trace_id={}, root.duration={}ms, 자식 합={}ms, 자식 수={}개"
                            + " — 합집합 산식 미적용 상태에서 평탄화된 중첩 호출이 원인일 가능성. 0으로 클램핑 후 저장.",
                    traceId, root.durationMs(), childrenSum, directChildren.size());
        }

        return Math.max(0L, raw);
    }

}