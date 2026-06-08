package com.apm.observatory.apiserver.metrics.repository;

import com.apm.observatory.apiserver.metrics.entity.ErosionTrendSlopeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ErosionTrendSlopeRepository extends JpaRepository<ErosionTrendSlopeEntity, String> {

    /** 앱별 최근 slope 1건 조회(summarizePerformance용). (app_name, timestamp DESC) 인덱스를 탄다. */
    @Query("SELECT e FROM ErosionTrendSlopeEntity e WHERE e.appName = :appName ORDER BY e.timestamp DESC LIMIT 1")
    Optional<ErosionTrendSlopeEntity> findLatestByAppName(@Param("appName") String appName);

}