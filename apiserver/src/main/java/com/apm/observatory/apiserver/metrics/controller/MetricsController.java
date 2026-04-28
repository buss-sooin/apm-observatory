package com.apm.observatory.apiserver.metrics.controller;

import com.apm.observatory.apiserver.metrics.model.MetricsModel.CurrentMetrics;
import com.apm.observatory.apiserver.metrics.model.MetricsModel.SummaryMetrics;
import com.apm.observatory.apiserver.metrics.model.MetricsModel.TrendPoint;
import com.apm.observatory.apiserver.metrics.port.MetricsPort;
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

@Tag(name = "Metrics", description = "자원 현황 API")
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsPort metricsPort;

    @Operation(summary = "현재 자원 현황", description = "앱의 최신 자원 스냅샷 조회")
    @GetMapping("/current")
    public ResponseEntity<CurrentMetrics> snapshotCurrent(
            @RequestParam("app_name") String appName) {
        return metricsPort.snapshotCurrent(appName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "자원 추세", description = "시간 범위 내 자원 시계열 조회 (그래프 원본 데이터)")
    @GetMapping("/trend")
    public ResponseEntity<List<TrendPoint>> traceTrend(
            @RequestParam("app_name") String appName,
            @RequestParam("start_time") Instant startTime,
            @RequestParam("end_time") Instant endTime) {
        return ResponseEntity.ok(metricsPort.traceTrend(appName, startTime, endTime));
    }

    @Operation(summary = "성능 요약", description = "구간 집계 + slope + 임계값 대비 수준")
    @GetMapping("/summary")
    public ResponseEntity<SummaryMetrics> summarizePerformance(
            @RequestParam("app_name") String appName,
            @RequestParam("start_time") Instant startTime,
            @RequestParam("end_time") Instant endTime) {
        return metricsPort.summarizePerformance(appName, startTime, endTime)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}