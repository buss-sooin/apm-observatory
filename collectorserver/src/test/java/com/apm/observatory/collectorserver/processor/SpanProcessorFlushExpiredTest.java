package com.apm.observatory.collectorserver.processor;

import com.apm.observatory.collectorserver.processor.adapter.SpanIngestionAdapter;
import com.apm.observatory.collectorserver.processor.repository.SpanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SpanProcessor.flushExpired의 trace 종료 판정과 처리 결과 명세.
 *
 * <p>idle 방식: 마지막 span 도착 후 10초간 추가 도착이 없으면 trace 종료.
 * 최대수명 상한: trace 생성 후 60초를 넘으면 idle 조건과 무관하게 강제 저장.
 *
 * <p>종료 판정 통과 후 두 갈래로 분기한다. ROOT 유무는 분기에 관여하지 않는다.
 * <ul>
 *   <li>정상 저장: saveAll 성공 → toAcknowledge에 recordId 추가</li>
 *   <li>saveAll 실패: spans와 recordIds를 DeadLetterEntry(reason=SAVE_FAILED)로 toDeadLetter에 추가</li>
 * </ul>
 *
 * <p>모든 경로에서 종료 판정 통과한 trace는 buffer에서 즉시 제거되어 누수가 없다.
 */
@DisplayName("SpanProcessor.flushExpired — trace 종료 판정과 처리 결과")
class SpanProcessorFlushExpiredTest {

    private static final long IDLE_THRESHOLD_MS = 10_000L;
    private static final long BASE_TIME_MS = 1_000_000_000L;

    private SpanRepository spanRepository;
    private SpanIngestionAdapter spanIngestionAdapter;
    private FakeClock clock;
    private SpanProcessor processor;
    private final AtomicLong recordIdCounter = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        spanRepository = mock(SpanRepository.class);
        spanIngestionAdapter = mock(SpanIngestionAdapter.class);
        clock = new FakeClock(BASE_TIME_MS);

        // assemble의 반환은 본 테스트의 검증 대상이 아니다. SpanProcessor가 saveAll을
        // 호출하려면 batchParams.isEmpty()가 false이기만 하면 되므로 빈 배열 한 개를
        // 담은 리스트로 충분하다. List.of(E... elements)는 varargs라서 new Object[0]을
        // 넘길 때 "배열을 풀어헤쳐 빈 리스트로 본다(E=Object)"와 "배열 한 개를 담은
        // 리스트로 본다(E=Object[])" 사이가 양가적이고, 자바 추론은 인자 표현식에서
        // 도출된 결정을 좌변 타입보다 우선해 해석 A로 가버린다. singletonList(T o)는
        // varargs가 아니라 단일 인자라 이 양가성 자체가 없다.
        when(spanIngestionAdapter.assemble(anyList()))
                .thenReturn(Collections.singletonList(new Object[0]));

        processor = SpanProcessor.newInstance(spanRepository, spanIngestionAdapter, clock);
    }

    @Test
    @DisplayName("마지막 span 도착 후 idle 임계 시간이 지나면 trace를 저장한다")
    void savesTraceWhenIdleThresholdExceeded() {
        processor.process(List.of(rootSpan("trace-A")));

        clock.advance(IDLE_THRESHOLD_MS);
        processor.flushExpired();

        verify(spanRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("마지막 span 도착 후 idle 임계 시간이 지나지 않았으면 trace를 buffer에 유지한다")
    void retainsTraceWhenIdleThresholdNotReached() {
        processor.process(List.of(rootSpan("trace-A")));

        clock.advance(IDLE_THRESHOLD_MS - 1);
        processor.flushExpired();

        verify(spanRepository, never()).saveAll(anyList());
        assertThat(processor.contains("trace-A")).isTrue();
    }

    @Test
    @DisplayName("새 span 도착으로 lastUpdatedAt이 갱신되면 idle 임계 계산은 다시 시작된다")
    void idleThresholdRestartsWhenNewSpanArrives() {
        processor.process(List.of(childSpan("trace-A")));
        clock.advance(IDLE_THRESHOLD_MS - 1_000);

        processor.process(List.of(rootSpan("trace-A")));
        clock.advance(IDLE_THRESHOLD_MS - 1);

        processor.flushExpired();

        verify(spanRepository, never()).saveAll(anyList());
        assertThat(processor.contains("trace-A")).isTrue();
    }

    @Test
    @DisplayName("trace 생성 후 최대수명 상한이 지나면 idle 조건과 무관하게 trace를 저장한다")
    void savesTraceWhenMaxLifetimeExceededRegardlessOfIdle() {
        processor.process(List.of(rootSpan("trace-A")));

        for (int i = 0; i < 12; i++) {
            clock.advance(5_000);
            processor.process(List.of(childSpan("trace-A")));
        }

        clock.advance(1);
        processor.flushExpired();

        verify(spanRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("idle 조건도 최대수명 상한도 만족하지 않으면 trace를 buffer에 유지한다")
    void retainsTraceWhenNeitherConditionMet() {
        processor.process(List.of(rootSpan("trace-A")));

        clock.advance(IDLE_THRESHOLD_MS - 1);
        processor.flushExpired();

        verify(spanRepository, never()).saveAll(anyList());
        assertThat(processor.contains("trace-A")).isTrue();
    }

    @Test
    @DisplayName("저장된 trace는 buffer에서 제거된다")
    void removesSavedTraceFromBuffer() {
        processor.process(List.of(rootSpan("trace-A")));

        clock.advance(IDLE_THRESHOLD_MS);
        processor.flushExpired();

        assertThat(processor.contains("trace-A")).isFalse();
        assertThat(processor.bufferedTraceCount()).isZero();
    }

    @Test
    @DisplayName("ROOT span이 있는 trace는 idle 조건 만족 시 저장된다")
    void savesTraceWithRootWhenIdle() {
        processor.process(List.of(childSpan("trace-A"), rootSpan("trace-A")));

        clock.advance(IDLE_THRESHOLD_MS);
        processor.flushExpired();

        verify(spanRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("저장 성공한 trace의 recordId는 toAcknowledge에 담겨 반환된다")
    void returnsRecordIdsOfSavedTraceInToAcknowledge() {
        IncomingSpan root = rootSpan("trace-A");
        processor.process(List.of(root));

        clock.advance(IDLE_THRESHOLD_MS);
        FlushResult result = processor.flushExpired();

        assertThat(result.toAcknowledge()).containsExactly(root.recordId());
        assertThat(result.toDeadLetter()).isEmpty();
    }

    @Test
    @DisplayName("ROOT 부재 trace도 idle 조건 만족 시 저장된다")
    void savesTraceWithoutRootWhenIdle() {
        IncomingSpan child = childSpan("trace-A");
        processor.process(List.of(child));

        clock.advance(IDLE_THRESHOLD_MS);
        FlushResult result = processor.flushExpired();

        verify(spanRepository, times(1)).saveAll(anyList());
        assertThat(processor.contains("trace-A")).isFalse();

        assertThat(result.toAcknowledge()).containsExactly(child.recordId());
        assertThat(result.toDeadLetter()).isEmpty();
    }

    @Test
    @DisplayName("saveAll 실패한 trace는 toDeadLetter에 SAVE_FAILED reason으로 담기고 buffer에서 제거된다")
    void movesSaveFailedTraceToDeadLetterAndRemovesFromBuffer() {
        doThrow(new RuntimeException("DB 제약 위반"))
                .when(spanRepository).saveAll(anyList());

        IncomingSpan root = rootSpan("trace-A");
        IncomingSpan child = childSpan("trace-A");
        processor.process(List.of(root, child));

        clock.advance(IDLE_THRESHOLD_MS);
        FlushResult result = processor.flushExpired();

        assertThat(processor.contains("trace-A")).isFalse();

        assertThat(result.toAcknowledge()).isEmpty();
        assertThat(result.toDeadLetter()).hasSize(1);
        DeadLetterEntry entry = result.toDeadLetter().get(0);
        assertThat(entry.reason()).startsWith("SAVE_FAILED:");
        assertThat(entry.reason()).contains("DB 제약 위반");
        assertThat(entry.recordIds()).containsExactly(root.recordId(), child.recordId());
        assertThat(entry.spans()).hasSize(2);
    }

    @Test
    @DisplayName("한 trace의 saveAll 실패가 다른 trace 처리에 영향을 주지 않는다")
    void saveFailureOfOneTraceDoesNotAffectAnother() {
        // 첫 호출은 실패, 두 번째 호출은 성공
        doThrow(new RuntimeException("일시 실패"))
                .doNothing()
                .when(spanRepository).saveAll(anyList());

        IncomingSpan rootA = rootSpan("trace-A");
        IncomingSpan rootB = rootSpan("trace-B");
        processor.process(List.of(rootA));
        processor.process(List.of(rootB));

        clock.advance(IDLE_THRESHOLD_MS);
        FlushResult result = processor.flushExpired();

        // 한 trace는 정상 ACK, 한 trace는 DLQ. 둘 다 buffer에서 제거
        assertThat(processor.bufferedTraceCount()).isZero();
        assertThat(result.toAcknowledge()).hasSize(1);
        assertThat(result.toDeadLetter()).hasSize(1);
    }

    // ===== 테스트 헬퍼 =====

    private IncomingSpan rootSpan(String traceId) {
        Map<String, String> m = new HashMap<>();
        m.put("trace_id", traceId);
        m.put("span_id", "root-" + traceId);
        m.put("parent_span_id", "");
        return new IncomingSpan(m, nextRecordId());
    }

    private IncomingSpan childSpan(String traceId) {
        Map<String, String> m = new HashMap<>();
        m.put("trace_id", traceId);
        m.put("span_id", "child-" + traceId + "-" + System.nanoTime());
        m.put("parent_span_id", "root-" + traceId);
        return new IncomingSpan(m, nextRecordId());
    }

    private RecordId nextRecordId() {
        return RecordId.of(recordIdCounter.getAndIncrement() + "-0");
    }

    /**
     * 테스트용 가짜 Clock. advance(millis)로 시간을 강제 이동.
     */
    static class FakeClock extends Clock {
        private long currentMillis;

        FakeClock(long startMillis) {
            this.currentMillis = startMillis;
        }

        void advance(long millis) {
            this.currentMillis += millis;
        }

        @Override
        public long millis() {
            return currentMillis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(currentMillis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

}