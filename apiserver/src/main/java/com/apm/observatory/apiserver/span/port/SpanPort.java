package com.apm.observatory.apiserver.span.port;

import com.apm.observatory.apiserver.span.model.SpanModel.WaterfallResponse;

import java.util.Optional;

public interface SpanPort {

    /**
     * traceId 하나로 전체 Span을 트리 구조로 조립해 반환한다.
     * 조립 방식은 구현(SpanAdapter)에 둔다.
     */
    Optional<WaterfallResponse> buildWaterfall(String traceId);

}