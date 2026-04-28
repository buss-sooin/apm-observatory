package com.apm.observatory.apiserver.span.port;

import com.apm.observatory.apiserver.span.model.SpanModel.WaterfallResponse;

import java.util.Optional;

public interface SpanPort {

    // 의도: traceId 하나로 전체 Span을 트리 구조로 조립해서 반환
    // flat rows → root 찾기 → BFS 트리 순회 → offsetMs, depth 계산
    Optional<WaterfallResponse> buildWaterfall(String traceId);

}