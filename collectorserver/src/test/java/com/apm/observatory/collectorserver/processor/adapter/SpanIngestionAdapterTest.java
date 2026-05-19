package com.apm.observatory.collectorserver.processor.adapter;

import com.apm.observatory.collectorserver.processor.TraceAssembler;
import com.apm.observatory.collectorserver.processor.adapter.Span;
import com.apm.observatory.collectorserver.processor.adapter.SpanIngestionAdapter;
import com.apm.observatory.collectorserver.processor.adapter.SpanType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpanIngestionAdapter: 도메인 Span 집합을 트레이스 계층으로 조립한다")
class SpanIngestionAdapterTest {

    private final SpanIngestionAdapter adapter =
            new SpanIngestionAdapter(new TraceAssembler());

    @Test
    @DisplayName("ROOT와 자식이 있으면 원본 전체에 INTERNAL이 ROOT 직속 자식으로 더해진다")
    void addsInternalAsRootChildWhenRootExists() {
        Span root = new Span("root-1", null, SpanType.ROOT, 100L);
        Span db = new Span("db-1", "root-1", SpanType.DB, 30L);
        Span ext = new Span("ext-1", "root-1", SpanType.EXTERNAL, 20L);

        List<Span> result = adapter.buildTraceSpans(List.of(root, db, ext));

        assertThat(result).contains(root, db, ext);

        Span internal = result.stream()
                .filter(s -> s.spanType() == SpanType.INTERNAL)
                .findFirst()
                .orElseThrow();
        assertThat(internal.parentSpanId()).isEqualTo("root-1");
        assertThat(internal.durationMs()).isEqualTo(50L);
    }

    @Test
    @DisplayName("자식이 없으면 INTERNAL duration은 ROOT 전체 시간과 같다")
    void internalEqualsRootWhenNoChildren() {
        Span root = new Span("root-1", null, SpanType.ROOT, 80L);

        List<Span> result = adapter.buildTraceSpans(List.of(root));

        Span internal = result.stream()
                .filter(s -> s.spanType() == SpanType.INTERNAL)
                .findFirst()
                .orElseThrow();
        assertThat(internal.durationMs()).isEqualTo(80L);
    }

    @Test
    @DisplayName("ROOT가 없으면 INTERNAL을 만들지 않고 원본을 그대로 반환한다")
    void returnsOriginalWhenRootMissing() {
        Span db = new Span("db-1", "root-x", SpanType.DB, 30L);
        Span ext = new Span("ext-1", "root-x", SpanType.EXTERNAL, 20L);

        List<Span> result = adapter.buildTraceSpans(List.of(db, ext));

        assertThat(result).containsExactlyInAnyOrder(db, ext);
        assertThat(result).noneMatch(s -> s.spanType() == SpanType.INTERNAL);
    }

}
