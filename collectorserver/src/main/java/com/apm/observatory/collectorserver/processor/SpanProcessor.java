package com.apm.observatory.collectorserver.processor;

import com.apm.observatory.collectorserver.repository.SpanRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SpanProcessor {

    private final SpanRepository spanRepository;

    // TraceID 기준 Span 수집 대기 맵
    // key: traceId, value: 해당 TraceID의 Span 목록
    // ConcurrentHashMap: Consumer 스레드와 타임아웃 스케줄러 스레드가 동시 접근
    private final ConcurrentHashMap<String, TraceBuffer> traceBuffers = new ConcurrentHashMap<>();

    private static final long SPAN_TIMEOUT_MS = 30_000L;

    public SpanProcessor(SpanRepository spanRepository) {
        this.spanRepository = spanRepository;
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

            List<Object[]> batchParams = buildBatchParams(spans);
            if (!batchParams.isEmpty()) {
                spanRepository.saveAll(batchParams);
            }
        });
    }

    private List<Object[]> buildBatchParams(List<Map<String, String>> spans) {
        List<Object[]> result = new ArrayList<>();

        // ROOT Span 찾기 — parent_span_id 없는 Span이 ROOT
        Optional<Map<String, String>> rootSpanOpt = spans.stream()
                .filter(s -> s.get("parent_span_id") == null
                        || s.get("parent_span_id").isEmpty())
                .findFirst();

        // INTERNAL Span 파생 계산
        // 후킹 대상: DispatcherServlet(ROOT), PreparedStatement(DB), RestClient(EXTERNAL)
        // INTERNAL = ROOT duration - sum(DB) - sum(EXTERNAL)
        // 단순 뺄셈으로 계산 — 정확히 하려면 모든 메서드를 직접 후킹해서 측정해야 할 것 같음
        // Math.max(0, ...): 측정 오차로 음수가 나올 수 있으므로 방어
        long internalDurationMs = rootSpanOpt.map(root -> {
            long rootDuration = parseLong(root.get("duration_ms"));
            long dbTotal = spans.stream()
                    .filter(s -> "DB".equals(s.get("span_type")))
                    .mapToLong(s -> parseLong(s.get("duration_ms")))
                    .sum();
            long externalTotal = spans.stream()
                    .filter(s -> "EXTERNAL".equals(s.get("span_type")))
                    .mapToLong(s -> parseLong(s.get("duration_ms")))
                    .sum();
            return Math.max(0, rootDuration - dbTotal - externalTotal);
        }).orElse(0L);

        // 수집된 Span 전체 저장
        for (Map<String, String> s : spans) {
            result.add(new Object[]{
                    s.get("span_id"),
                    s.get("trace_id"),
                    s.get("parent_span_id"),
                    s.get("app_name"),
                    s.get("host"),
                    s.get("span_type"),
                    toIso(s.get("start_time")),
                    toIso(s.get("end_time")),
                    parseLong(s.get("duration_ms")),
                    s.get("http_method"),
                    s.get("http_url"),
                    s.get("http_status") != null ? Integer.parseInt(s.get("http_status")) : null,
                    s.get("sql_query"),
                    s.get("external_host"),
                    Boolean.parseBoolean(s.get("error")),
                    s.get("error_message")
            });
        }

        // ROOT 있으면 INTERNAL 항상 추가
        // 0이면 측정 오차(DB + EXTERNAL 합계가 ROOT 초과)로 해석
        rootSpanOpt.ifPresent(root -> result.add(new Object[]{
                java.util.UUID.randomUUID().toString(),
                root.get("trace_id"),
                root.get("span_id"),
                root.get("app_name"),
                root.get("host"),
                "INTERNAL",
                toIso(root.get("start_time")),
                toIso(root.get("end_time")),
                internalDurationMs,
                null, null, null, null, null, false, null
        }));

        return result;
    }

    private long parseLong(String val) {
        if (val == null || val.isBlank()) return 0L;
        return Long.parseLong(val);
    }

    // epoch milliseconds → ISO-8601 문자열
    // PostgreSQL ?::timestamptz 가 문자열을 받으므로 변환 필요
    private String toIso(String epochMs) {
        if (epochMs == null || epochMs.isBlank()) return null;
        return Instant.ofEpochMilli(Long.parseLong(epochMs)).toString();
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