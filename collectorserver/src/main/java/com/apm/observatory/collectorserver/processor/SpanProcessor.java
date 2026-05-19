package com.apm.observatory.collectorserver.processor;

import com.apm.observatory.collectorserver.processor.adapter.SpanIngestionAdapter;
import com.apm.observatory.collectorserver.processor.repository.SpanRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SpanProcessor {

    private final SpanRepository spanRepository;
    private final SpanIngestionAdapter spanIngestionAdapter;

    // TraceID 기준 Span 수집 대기 맵
    // key: traceId, value: 해당 TraceID의 Span 목록
    // ConcurrentHashMap: Consumer 스레드와 타임아웃 스케줄러 스레드가 동시 접근
    private final ConcurrentHashMap<String, TraceBuffer> traceBuffers = new ConcurrentHashMap<>();

    private static final long SPAN_TIMEOUT_MS = 30_000L;

    public SpanProcessor(SpanRepository spanRepository,
                         SpanIngestionAdapter spanIngestionAdapter) {
        this.spanRepository = spanRepository;
        this.spanIngestionAdapter = spanIngestionAdapter;
    }

    public void process(List<Map<String, String>> messages) {
        if (messages.isEmpty()) return;

        for (Map<String, String> m : messages) {
            String traceId = m.get("trace_id");
            traceBuffers.computeIfAbsent(traceId, k -> new TraceBuffer()).add(m);
        }
    }

    // 스케줄러가 주기적으로 호출 — 타임아웃된 TraceID 처리
    public void flushExpired() {
        long now = System.currentTimeMillis();

        traceBuffers.forEach((traceId, buffer) -> {
            if (now - buffer.createdAt < SPAN_TIMEOUT_MS) return;

            // 30초 지난 TraceID는 결과와 관계없이 제거
            // 지금은 타임아웃되면 그냥 제거하는데, 저장 실패와 구분해서 재시도할 수 있으면 더 좋을 것 같음
            traceBuffers.remove(traceId);

            List<Map<String, String>> spans = buffer.spans;

            // ROOT Span 없으면 드롭
            // ROOT Span은 구조적으로 가장 늦게 도착 — 30초 후에도 없으면
            // 에이전트 전송 실패 또는 큐 드롭으로 판단
            // 루트 Span이 없는 트레이스를 그냥 버리는데, 불완전하다는 표시라도 남기는 게 나을 것 같음
            boolean rootMissing = spans.stream()
                    .noneMatch(s -> s.get("parent_span_id") == null
                            || s.get("parent_span_id").isEmpty());
            if (rootMissing) return;

            List<Object[]> batchParams = spanIngestionAdapter.assemble(spans);
            if (!batchParams.isEmpty()) {
                spanRepository.saveAll(batchParams);
            }
        });
    }

    // TraceID 기준 Span 수집 버퍼
    private static class TraceBuffer {
        final List<Map<String, String>> spans = new ArrayList<>();
        final long createdAt = System.currentTimeMillis();

        void add(Map<String, String> span) {
            spans.add(span);
        }
    }

}