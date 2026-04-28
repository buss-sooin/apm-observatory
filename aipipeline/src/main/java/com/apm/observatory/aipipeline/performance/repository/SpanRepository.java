package com.apm.observatory.aipipeline.performance.repository;

import com.apm.observatory.aipipeline.performance.entity.SpanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SpanRepository extends JpaRepository<SpanEntity, String> {

    // 최근 N분 특정 앱의 전체 Span 조회
    List<SpanEntity> findByAppNameAndStartTimeBetween(
            String appName,
            Instant start,
            Instant end
    );

    // 최근 N분 특정 span_type의 Span 조회 (EXTERNAL 필터링용)
    List<SpanEntity> findByAppNameAndSpanTypeAndStartTimeBetween(
            String appName,
            String spanType,
            Instant start,
            Instant end
    );

    // 평소 기준 특정 span_type 평균 응답시간
    @Query("SELECT AVG(s.durationMs) FROM SpanEntity s " +
            "WHERE s.appName = :appName " +
            "AND s.spanType = :spanType " +
            "AND s.startTime BETWEEN :start AND :end")
    Double findAvgDurationMs(
            @Param("appName") String appName,
            @Param("spanType") String spanType,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

}