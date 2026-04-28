package com.apm.observatory.apiserver.log.controller;

import com.apm.observatory.apiserver.log.adapter.LogAdapter;
import com.apm.observatory.apiserver.log.model.LogModel.LogEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Tag(name = "Logs", description = "로그 스트림 API")
@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogAdapter logAdapter;

    @Operation(summary = "로그 스트림",
            description = "시간 범위 내 로그 조회. level 파라미터 없으면 전체, 있으면 해당 레벨만 (예: ERROR, INFO, WARN)")
    @GetMapping("/stream")
    public ResponseEntity<List<LogEntry>> stream(
            @RequestParam("app_name") String appName,
            @RequestParam("start_time") Instant startTime,
            @RequestParam("end_time") Instant endTime,
            @RequestParam(value = "level", required = false) String level) {
        return ResponseEntity.ok(logAdapter.streamLogs(appName, startTime, endTime, level));
    }

}