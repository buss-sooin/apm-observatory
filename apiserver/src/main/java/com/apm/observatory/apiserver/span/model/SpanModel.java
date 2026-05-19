package com.apm.observatory.apiserver.span.model;

import java.time.Instant;
import java.util.List;

public class SpanModel {

    // GET /spans/waterfall 응답
    public record WaterfallResponse(
            String traceId,
            long totalDurationMs,   // root span의 duration → 전체 타임라인 너비
            Instant startTime,      // root span의 start_time → 타임라인 기준점
            List<WaterfallSpan> spans
    ) {}

    // 폭포수 차트 개별 Span
    // 의도: DB flat rows → 트리 구조 표현에 필요한 파생값 포함
    public record WaterfallSpan(
            String spanId,
            String parentSpanId,
            String spanType,        // ROOT / INTERNAL / DB / EXTERNAL
            long startOffsetMs,     // root 시작 기준 상대 오프셋 = span.startTime - root.startTime
            long durationMs,
            int depth,              // 트리 깊이 (들여쓰기 표현용) root=0, 자식=1, 손자=2
            String httpMethod,
            String httpUrl,
            Integer httpStatus,
            String sqlQuery,
            String externalHost,
            boolean error
    ) {}

}