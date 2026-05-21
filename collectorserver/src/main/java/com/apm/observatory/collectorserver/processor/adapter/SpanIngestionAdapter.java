package com.apm.observatory.collectorserver.processor.adapter;

import com.apm.observatory.collectorserver.processor.Span;
import com.apm.observatory.collectorserver.processor.SpanType;
import com.apm.observatory.collectorserver.processor.TraceAssembler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 수집된 span을 저장 가능한 트레이스 계층으로 가공하는 입력 경계.
 *
 * <p>collectorserver의 process 흐름(받은 데이터 → 저장)에서, 그 사이에 놓인
 * "가공" 단계를 인프라 경계로 분리한 컴포넌트다. process는 흐름을 책임으로
 * 그대로 유지하고, 받은 데이터를 도메인 표현으로 바꾸고 트리를 조립해 저장
 * 형태로 되돌리는 경계 작업을 이 클래스가 흡수한다.
 *
 * <p>이 클래스는 인프라 양쪽 경계를 감싼다. 입력은 Redis Streams에서 온
 * {@code Map<String,String>}, 출력은 JDBC INSERT가 요구하는 {@code Object[]}다.
 * 그 사이의 순수 계산(INTERNAL duration 산식)은 {@link TraceAssembler}에
 * 위임한다. 가공이 먼저, 그 안에서 계산이 호출되는 선후관계를 이 분리가
 * 코드 구조로 드러낸다.
 */
@Component
public class SpanIngestionAdapter {

    private final TraceAssembler traceAssembler;

    public SpanIngestionAdapter(TraceAssembler traceAssembler) {
        this.traceAssembler = traceAssembler;
    }

    /**
     * process가 호출하는 가공 진입점.
     *
     * <p>Redis에서 온 Map 목록을 받아 도메인 Span으로 바꾸고, 트리를 조립한 뒤
     * JDBC 저장 형태({@code Object[]})로 되돌린다. Map 파싱과 Object[] 매핑은
     * 인프라 세부이고, 그 사이의 트리 판단은 {@link #buildTraceSpans}에 위임한다.
     *
     * @param rawSpans Redis Streams에서 수신한 span Map 목록
     * @return SpanRepository.saveAll이 받는 Object[] 목록
     */
    public List<Object[]> assemble(List<Map<String, String>> rawSpans) {
        List<Object[]> result = new java.util.ArrayList<>();

        // 원본 span 전체 → Object[] (저장 인프라 경계, 기존 컬럼 순서 유지)
        for (Map<String, String> s : rawSpans) {
            result.add(toRow(s));
        }

        // Map → 도메인 Span(트리 조립에 필요한 4필드만) → 트리 조립
        List<Span> domainSpans = rawSpans.stream()
                .map(this::toSpan)
                .toList();
        List<Span> assembled = buildTraceSpans(domainSpans);

        // 조립으로 새로 생긴 INTERNAL만 Object[]로 추가.
        // 원본 span은 위에서 이미 변환했으므로, buildTraceSpans가 더한 INTERNAL만 가려낸다.
        assembled.stream()
                .filter(s -> s.spanType() == SpanType.INTERNAL)
                .forEach(internal -> result.add(internalRow(internal, rawSpans)));

        return result;
    }

    // Map → 도메인 Span. 트리 조립 판단에 필요한 4필드만 추출한다.
    // parent_span_id의 빈 문자열은 null로 정규화해 ROOT 식별을 일치시킨다.
    private Span toSpan(Map<String, String> s) {
        String parent = s.get("parent_span_id");
        if (parent != null && parent.isEmpty()) {
            parent = null;
        }
        return new Span(
                s.get("span_id"),
                parent,
                toSpanType(s.get("span_type")),
                parseLong(s.get("duration_ms"))
        );
    }

    // 외부 경계 값 방어: 알 수 없는 값이나 null이면 UNKNOWN
    private SpanType toSpanType(String raw) {
        if (raw == null) {
            return SpanType.UNKNOWN;
        }
        try {
            return SpanType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return SpanType.UNKNOWN;
        }
    }

    // 원본 span Map → 저장 Object[] (SpanRepository INSERT 16컬럼 순서)
    private Object[] toRow(Map<String, String> s) {
        return new Object[]{
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
        };
    }

    // 파생 INTERNAL → 저장 Object[]. ROOT의 식별/시간 메타를 따르고,
    // http·sql 등 후킹 전용 필드는 INTERNAL에 해당 없음(null).
    private Object[] internalRow(Span internal, List<Map<String, String>> rawSpans) {
        Map<String, String> root = rawSpans.stream()
                .filter(s -> internal.parentSpanId().equals(s.get("span_id")))
                .findFirst()
                .orElseThrow();
        return new Object[]{
                internal.spanId(),
                root.get("trace_id"),
                internal.parentSpanId(),
                root.get("app_name"),
                root.get("host"),
                SpanType.INTERNAL.name(),
                toIso(root.get("start_time")),
                toIso(root.get("end_time")),
                internal.durationMs(),
                null, null, null, null, null, false, null
        };
    }

    private long parseLong(String val) {
        if (val == null || val.isBlank()) return 0L;
        return Long.parseLong(val);
    }

    // epoch milliseconds → ISO-8601 문자열 (PostgreSQL ?::timestamptz 입력용)
    private String toIso(String epochMs) {
        if (epochMs == null || epochMs.isBlank()) return null;
        return java.time.Instant.ofEpochMilli(Long.parseLong(epochMs)).toString();
    }

    /**
     * 도메인 Span 집합을 트레이스 계층으로 조립한다.
     *
     * <p>ROOT(부모 없는 span)를 식별하고, 그 직속 자식(parentSpanId == root.spanId)을
     * 가른 뒤 {@link TraceAssembler}로 INTERNAL duration을 계산해, 원본 전체에
     * 파생 INTERNAL span을 더한 목록을 반환한다.
     *
     * <p>INTERNAL span은 ROOT의 직속 자식으로 조립한다(parentSpanId = ROOT.spanId).
     * ROOT가 없으면 INTERNAL을 만들 수 없으므로 원본을 그대로 반환한다. ROOT 부재를
     * 불완전으로 표시·저장하는 처리는 계산식 3의 범위이며 이 메서드 밖이다.
     *
     * @param spans 한 트레이스로 모인 도메인 Span 목록
     * @return 원본 전체 + 파생 INTERNAL(ROOT 존재 시)
     */
    List<Span> buildTraceSpans(List<Span> spans) {
        Span root = spans.stream()
                .filter(s -> s.parentSpanId() == null)
                .findFirst()
                .orElse(null);

        if (root == null) {
            return spans;
        }

        List<Span> directChildren = spans.stream()
                .filter(s -> root.spanId().equals(s.parentSpanId()))
                .toList();

        long internalDuration =
                traceAssembler.calculateInternalDuration(root, directChildren);

        Span internal = new Span(
                java.util.UUID.randomUUID().toString(),
                root.spanId(),
                SpanType.INTERNAL,
                internalDuration
        );

        List<Span> result = new java.util.ArrayList<>(spans);
        result.add(internal);
        return result;
    }

}