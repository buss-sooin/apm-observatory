package com.apm.observatory.aipipeline.performance.repository;

import com.apm.observatory.aipipeline.performance.entity.MetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MetricsRepository extends JpaRepository<MetricsEntity, MetricsEntity.MetricsPK> {

    List<MetricsEntity> findByAppNameAndTimestampBetween(
            String appName,
            Instant start,
            Instant end
    );

    /** 기간 평균 CPU. baseline 비교 기준값으로 쓴다. */
    @Query("SELECT AVG(m.cpuUsage) FROM MetricsEntity m " +
            "WHERE m.appName = :appName " +
            "AND m.timestamp BETWEEN :start AND :end")
    Double findAvgCpuUsage(
            @Param("appName") String appName,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    /** 기간 평균 heap 사용량. baseline 비교 기준값으로 쓴다. */
    @Query("SELECT AVG(m.heapUsed) FROM MetricsEntity m " +
            "WHERE m.appName = :appName " +
            "AND m.timestamp BETWEEN :start AND :end")
    Double findAvgHeapUsed(
            @Param("appName") String appName,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

}
