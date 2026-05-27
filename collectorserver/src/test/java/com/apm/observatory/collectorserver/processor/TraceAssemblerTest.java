package com.apm.observatory.collectorserver.processor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TraceAssembler: 트레이스로 모인 span을 저장 가능한 계층으로 조립한다")
class TraceAssemblerTest {

    private final TraceAssembler assembler = new TraceAssembler();

    @Test
    @DisplayName("자식 호출이 없으면 INTERNAL duration은 ROOT 전체 시간과 같다")
    void internalEqualsRootWhenNoChildren() {
        Span root = new Span("root-1", null, SpanType.ROOT, 100L);

        long internal = assembler.calculateInternalDuration("trace-1", root, List.of());

        assertThat(internal).isEqualTo(100L);
    }

    @Test
    @DisplayName("자식이 있으면 INTERNAL duration은 ROOT에서 직속 자식 합을 뺀 값이다")
    void internalIsRootMinusChildrenSum() {
        Span root = new Span("root-1", null, SpanType.ROOT, 100L);
        List<Span> children = List.of(
                new Span("child-db", "root-1", SpanType.DB, 30L),
                new Span("child-ext", "root-1", SpanType.EXTERNAL, 20L)
        );

        long internal = assembler.calculateInternalDuration("trace-1", root, children);

        assertThat(internal).isEqualTo(50L);
    }

    @Test
    @DisplayName("자식 합이 ROOT를 초과하면(측정 오차) INTERNAL duration은 0이다")
    void internalClampedToZeroWhenChildrenExceedRoot() {
        Span root = new Span("root-1", null, SpanType.ROOT, 40L);
        List<Span> children = List.of(
                new Span("child-db", "root-1", SpanType.DB, 30L),
                new Span("child-ext", "root-1", SpanType.EXTERNAL, 20L)
        );

        long internal = assembler.calculateInternalDuration("trace-1", root, children);

        assertThat(internal).isZero();
    }

    @Test
    @DisplayName("종류를 모르는 자식도 직속 자식이면 차감에 포함된다")
    void unknownTypedChildIsStillSubtracted() {
        Span root = new Span("root-1", null, SpanType.ROOT, 100L);
        List<Span> children = List.of(
                new Span("child-db", "root-1", SpanType.DB, 30L),
                new Span("child-unknown", "root-1", SpanType.UNKNOWN, 25L)
        );

        long internal = assembler.calculateInternalDuration("trace-1", root, children);

        assertThat(internal).isEqualTo(45L);
    }

}