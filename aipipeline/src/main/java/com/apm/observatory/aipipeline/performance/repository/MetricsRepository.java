package com.apm.observatory.aipipeline.performance.repository;

import com.apm.observatory.aipipeline.performance.entity.MetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MetricsRepository extends JpaRepository<MetricsEntity, MetricsEntity.MetricsPK> {

    // 최근 N분 특정 앱의 Metrics 조회
    List<MetricsEntity> findByAppNameAndTimestampBetween(
            String appName,
            Instant start,
            Instant end
    );

    // 평소 기준 CPU 평균 조회 (선형 회귀, 급등 판단용)
    @Query("SELECT AVG(m.cpuUsage) FROM MetricsEntity m " +
            "WHERE m.appName = :appName " +
            "AND m.timestamp BETWEEN :start AND :end")
    Double findAvgCpuUsage(
            @Param("appName") String appName,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    // 평소 기준 메모리 사용률 평균 조회
    @Query("SELECT AVG(m.heapUsed) FROM MetricsEntity m " +
            "WHERE m.appName = :appName " +
            "AND m.timestamp BETWEEN :start AND :end")
    Double findAvgHeapUsed(
            @Param("appName") String appName,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

}