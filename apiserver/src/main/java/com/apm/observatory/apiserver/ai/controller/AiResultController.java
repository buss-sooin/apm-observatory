package com.apm.observatory.apiserver.ai.controller;

import com.apm.observatory.apiserver.ai.adapter.AiResultAdapter;
import com.apm.observatory.apiserver.ai.model.AiModel.AiResultSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI Results", description = "AI 분석 결과 조회 API")
@RestController
@RequestMapping("/ai/results")
@RequiredArgsConstructor
public class AiResultController {

    private final AiResultAdapter aiResultAdapter;

    @Operation(summary = "AI 분석 결과 목록 조회",
            description = "app_name 기준 AI 분석 결과 목록. timestamp 내림차순.")
    @GetMapping
    public ResponseEntity<List<AiResultSummary>> findByAppName(
            @RequestParam("app_name") String appName) {
        return ResponseEntity.ok(aiResultAdapter.findByAppName(appName));
    }

    @Operation(summary = "AI 분석 결과 단건 조회",
            description = "id로 단건 조회. 없으면 404.")
    @GetMapping("/{id}")
    public ResponseEntity<AiResultSummary> findById(@PathVariable String id) {
        return aiResultAdapter.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}