package com.apm.observatory.collectorserver.processor;

import com.apm.observatory.collectorserver.config.CollectorConfig;
import com.apm.observatory.collectorserver.processor.adapter.SpanIngestionAdapter;
import com.apm.observatory.collectorserver.processor.repository.SpanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 수신 span을 traceId 단위로 모았다가 완성된 트레이스를 저장 경계로 넘기는 버퍼링 컴포넌트.
 *
 * <p>한 트레이스의 span들은 서로 다른 시점에 도착한다. process는 도착하는 족족
 * traceId별 버퍼에 쌓고, 판단은 하지 않는다. 트레이스가 다 모였는지를 도착
 * 이벤트로는 알 수 없으므로, flushExpired가 주기적으로 idle 판정과 최대수명 상한
 * 판정을 거쳐 종료된 trace를 저장 경계로 넘긴다.
 *
 * <p>종료 판정은 두 조건의 OR다.
 * <ul>
 *   <li>idle 조건: 마지막 span 도착 후 {@link CollectorConfig#IDLE_THRESHOLD_MS}
 *       동안 추가 span이 없으면 종료. 실제 trace 길이에 비례하는 지연으로 저장된다.
 *   <li>최대수명 조건: trace 생성 후 {@link CollectorConfig#MAX_LIFETIME_MS}를 넘으면
 *       idle 조건과 무관하게 강제 저장. 끝없이 span이 들어오는 비정상 케이스 방어.
 * </ul>
 *
 * <p>traceBuffers는 두 스레드가 동시에 접근한다. span을 쌓는 Consumer 스레드와
 * 타임아웃을 검사·제거하는 스케줄러 스레드다. 이 동시 접근 때문에
 * {@link java.util.concurrent.ConcurrentHashMap}을 쓴다.
 *
 * <p>시간 의존성은 {@link Clock}으로 추상화한다. 운영 진입점인 public 생성자는 시스템
 * 시각을 쓰고, 테스트는 {@link #newInstance(SpanRepository, SpanIngestionAdapter, Clock)}
 * 정적 팩토리로 가짜 Clock을 주입해 시간을 통제한다. 운영과 테스트의 진입점을 시그니처가
 * 아닌 이름으로 구분해 호출 의도를 코드에서 드러낸다.
 *
 * @see SpanIngestionAdapter 모은 span을 트레이스 계층으로 조립하는 인프라 경계
 */
@Component
public class SpanProcessor {

    private final SpanRepository spanRepository;
    private final SpanIngestionAdapter spanIngestionAdapter;
    private final Clock clock;

    // key: traceId, value: 그 traceId로 모인 span 버퍼
    private final ConcurrentHashMap<String, TraceBuffer> traceBuffers = new ConcurrentHashMap<>();

    /**
     * 운영용 생성자. Spring DI가 호출. 시스템 시각으로 동작한다.
     *
     * <p>클래스에서 유일한 public 생성자다. Spring 4.3+는 단일 public 생성자에
     * {@code @Autowired}가 없어도 자동으로 선택해 주입한다.
     */
    @Autowired
    public SpanProcessor(SpanRepository spanRepository,
                         SpanIngestionAdapter spanIngestionAdapter) {
        this(spanRepository, spanIngestionAdapter, Clock.systemUTC());
    }

    /**
     * 테스트용 정적 팩토리 메서드. 같은 패키지 가시성으로 두어 같은 패키지의 테스트만
     * 호출 가능. 가짜 Clock 주입을 통해 시간 의존 로직을 검증한다.
     *
     * <p>{@code new SpanProcessor(repo, adapter, clock)} 형태의 생성자 대신 이름 있는
     * 정적 팩토리로 분리한 이유는 운영 진입점(public 생성자)과 테스트 진입점을 시그니처가
     * 아니라 이름으로 구분하기 위함이다.
     *
     * @param spanRepository       저장 인프라 경계
     * @param spanIngestionAdapter 가공 인프라 경계
     * @param clock                테스트가 주입하는 가짜 시계
     * @return Clock이 주입된 SpanProcessor 인스턴스
     */
    static SpanProcessor newInstance(SpanRepository spanRepository,
                                     SpanIngestionAdapter spanIngestionAdapter,
                                     Clock clock) {
        return new SpanProcessor(spanRepository, spanIngestionAdapter, clock);
    }

    /**
     * 내부 위임용 private 생성자. 운영 생성자와 정적 팩토리 메서드 모두 이 생성자로 위임한다.
     * private이므로 Spring DI 자동 선택 후보가 아니다.
     */
    private SpanProcessor(SpanRepository spanRepository,
                          SpanIngestionAdapter spanIngestionAdapter,
                          Clock clock) {
        this.spanRepository = spanRepository;
        this.spanIngestionAdapter = spanIngestionAdapter;
        this.clock = clock;
    }

    public void process(List<Map<String, String>> messages) {
        if (messages.isEmpty()) return;

        long now = clock.millis();
        for (Map<String, String> m : messages) {
            String traceId = m.get("trace_id");
            traceBuffers.computeIfAbsent(traceId, k -> new TraceBuffer(now)).add(m, now);
        }
    }

    // 스케줄러가 주기적으로 호출 — 종료 판정 대상 trace 처리
    public void flushExpired() {
        long now = clock.millis();

        traceBuffers.forEach((traceId, buffer) -> {
            boolean idleExceeded = now - buffer.lastUpdatedAt >= CollectorConfig.IDLE_THRESHOLD_MS;
            boolean maxLifetimeExceeded = now - buffer.createdAt >= CollectorConfig.MAX_LIFETIME_MS;
            if (!idleExceeded && !maxLifetimeExceeded) return;

            List<Map<String, String>> spans = buffer.spans;

            // ROOT span 누락 검사. ROOT는 구조적으로 가장 늦게 도착하므로 종료 판정
            // 시점까지 안 왔다면 에이전트 전송 실패 또는 큐 드롭으로 판단해 통째로 드롭한다.
            // 본 변경의 범위는 idle 방식 도입에 한정하므로 기존 정책을 유지한다.
            // 불완전 표시 등의 발전 방향은 별개 작업으로 분리한다.
            boolean rootMissing = spans.stream()
                    .noneMatch(s -> s.get("parent_span_id") == null
                            || s.get("parent_span_id").isEmpty());
            if (rootMissing) {
                traceBuffers.remove(traceId);
                return;
            }

            List<Object[]> batchParams = spanIngestionAdapter.assemble(spans);
            if (!batchParams.isEmpty()) {
                spanRepository.saveAll(batchParams);
            }
            traceBuffers.remove(traceId);
        });
    }

    /**
     * 현재 buffer에 있는 trace 개수.
     *
     * <p>collectorserver가 모으는 중인(아직 종료 판정 전인) trace의 개수다.
     * 운영 관측 지표로 의미 있고, 본 작업의 테스트 검증에도 쓰인다.
     */
    int bufferedTraceCount() {
        return traceBuffers.size();
    }

    /**
     * 특정 traceId가 현재 buffer에 있는지 확인한다.
     *
     * <p>buffer는 아직 종료 판정 전인 trace를 담는다. 종료 판정으로 저장되거나
     * 드롭되면 buffer에서 제거된다. 본 작업의 테스트 검증에 쓰인다.
     *
     * @param traceId 확인할 traceId
     * @return buffer에 있으면 true
     */
    boolean contains(String traceId) {
        return traceBuffers.containsKey(traceId);
    }

    /**
     * traceId 기준 Span 수집 버퍼.
     *
     * <p>시간 추상화를 자체적으로 갖지 않는다. outer가 결정한 시각을 인자로 받아 저장한다.
     * TraceBuffer가 시간 의존성을 모르도록 유지해 단일 책임을 지킨다.
     *
     * <p>lastUpdatedAt은 add 호출 시마다 갱신되어 idle 판정의 기준이 된다. createdAt은
     * 최초 생성 시각으로 고정되어 최대수명 상한 판정의 기준이 된다.
     */
    private static class TraceBuffer {
        final List<Map<String, String>> spans = new ArrayList<>();
        final long createdAt;
        volatile long lastUpdatedAt;

        TraceBuffer(long now) {
            this.createdAt = now;
            this.lastUpdatedAt = now;
        }

        void add(Map<String, String> span, long now) {
            spans.add(span);
            lastUpdatedAt = now;
        }
    }

}