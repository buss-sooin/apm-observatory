package com.apm.observatory.apiserver.config.controller;

import com.apm.observatory.apiserver.config.adapter.ConfigAdapter;
import com.apm.observatory.apiserver.config.model.ConfigModel.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Config", description = "설정 API (ADMIN 전용)")
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigAdapter configAdapter;

    @Operation(summary = "임계값 설정",
            description = "ADMIN 전용. appName 필수. 나머지 null이면 기존값 유지. 없으면 기본값으로 신규 생성.")
    @PostMapping("/threshold")
    public ResponseEntity<ThresholdResponse> upsertThreshold(
            @RequestBody ThresholdRequest request) {
        return ResponseEntity.ok(configAdapter.upsertThreshold(request));
    }

    @Operation(summary = "비즈니스 사이클 설정",
            description = "ADMIN 전용. appName 필수. 설정 시 aipipeline이 전날 동시간대를 baseline으로 사용. " +
                    "미설정 시 최근 N분 평균을 baseline으로 사용(기본 동작).")
    @PostMapping("/business-cycle")
    public ResponseEntity<BusinessCycleResponse> upsertBusinessCycle(
            @RequestBody BusinessCycleRequest request) {
        return ResponseEntity.ok(configAdapter.upsertBusinessCycle(request));
    }

    @Operation(summary = "비즈니스 사이클 삭제",
            description = "ADMIN 전용. 삭제 시 aipipeline이 baseline fallback(최근 N분 평균)으로 돌아감.")
    @DeleteMapping("/business-cycle")
    public ResponseEntity<Void> deleteBusinessCycle(
            @RequestParam("app_name") String appName) {
        configAdapter.deleteBusinessCycle(appName);
        return ResponseEntity.noContent().build();
    }

}