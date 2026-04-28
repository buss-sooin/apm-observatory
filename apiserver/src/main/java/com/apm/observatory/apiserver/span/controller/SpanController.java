package com.apm.observatory.apiserver.span.controller;

import com.apm.observatory.apiserver.span.model.SpanModel.WaterfallResponse;
import com.apm.observatory.apiserver.span.port.SpanPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Spans", description = "트레이스 Span API")
@RestController
@RequestMapping("/spans")
@RequiredArgsConstructor
public class SpanController {

    private final SpanPort spanPort;

    @Operation(summary = "폭포수 차트", description = "traceId 기준 전체 Span 트리 조립 + offsetMs + depth 반환")
    @GetMapping("/waterfall")
    public ResponseEntity<WaterfallResponse> waterfall(@RequestParam("trace_id") String traceId) {
        return spanPort.buildWaterfall(traceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}