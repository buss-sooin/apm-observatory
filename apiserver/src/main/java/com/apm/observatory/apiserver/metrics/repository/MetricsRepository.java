package com.apm.observatory.apiserver.metrics.repository;

import com.apm.observatory.apiserver.metrics.entity.MetricsEntity;
import com.apm.observatory.apiserver.metrics.entity.MetricsPk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MetricsRepository extends JpaRepository<MetricsEntity, MetricsPk> {

    /** 최신 1건 조회(snapshotCurrent용). timestamp 내림차순 1건. */
    @Query("SELECT m FROM MetricsEntity m WHERE m.id.appName = :appName ORDER BY m.id.timestamp DESC LIMIT 1")
    Optional<MetricsEntity> findLatestByAppName(@Param("appName") String appName);

    /** 시간 범위 내 시계열 조회(traceTrend용). timestamp 오름차순. */
    @Query("SELECT m FROM MetricsEntity m WHERE m.id.appName = :appName AND m.id.timestamp BETWEEN :start AND :end ORDER BY m.id.timestamp ASC")
    List<MetricsEntity> findByAppNameAndTimestampBetween(
            @Param("appName") String appName,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /** 구간 CPU 평균(summarizePerformance용). 결과 없으면 null이므로 Adapter에서 방어한다. */
    @Query("SELECT AVG(m.cpuUsage) FROM MetricsEntity m WHERE m.id.appName = :appName AND m.id.timestamp BETWEEN :start AND :end")
    Double findAvgCpuUsage(
            @Param("appName") String appName,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /** 구간 heap 사용률(%) 평균(summarizePerformance용). JPQL에서 heap_used / heap_max * 100을 계산한다. */
    @Query("SELECT AVG(m.heapUsed * 1.0 / m.heapMax * 100) FROM MetricsEntity m WHERE m.id.appName = :appName AND m.id.timestamp BETWEEN :start AND :end")
    Double findAvgHeapUsagePercent(
            @Param("appName") String appName,
            @Param("start") Instant start,
            @Param("end") Instant end);

}