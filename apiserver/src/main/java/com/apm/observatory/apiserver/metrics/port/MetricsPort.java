package com.apm.observatory.apiserver.metrics.port;

import com.apm.observatory.apiserver.metrics.model.MetricsModel.CurrentMetrics;
import com.apm.observatory.apiserver.metrics.model.MetricsModel.SummaryMetrics;
import com.apm.observatory.apiserver.metrics.model.MetricsModel.TrendPoint;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MetricsPort {

    // 의도: 현재 자원 현황 위젯용 → 최신 1건
    Optional<CurrentMetrics> snapshotCurrent(String appName);

    // 의도: 추세 그래프 위젯용 → 시간 범위 내 시계열 rows
    List<TrendPoint> traceTrend(String appName, Instant startTime, Instant endTime);

    // 의도: 성능 요약 위젯용 → 집계 + slope + 임계값 대비 수준
    // metrics, threshold_config, erosion_trend_slopes 세 테이블 조합
    Optional<SummaryMetrics> summarizePerformance(String appName, Instant startTime, Instant endTime);

}