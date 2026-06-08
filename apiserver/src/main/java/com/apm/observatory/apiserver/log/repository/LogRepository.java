package com.apm.observatory.apiserver.log.repository;

import com.apm.observatory.apiserver.log.entity.LogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LogRepository extends JpaRepository<LogEntity, LogEntity.LogPk> {

    /** 시간 범위 내 전체 로그를 timestamp 오름차순으로 조회한다(level 필터 없음). */
    @Query("SELECT l FROM LogEntity l WHERE l.id.appName = :appName " +
            "AND l.id.timestamp BETWEEN :start AND :end " +
            "ORDER BY l.id.timestamp ASC")
    List<LogEntity> findStream(
            @Param("appName") String appName,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /** 시간 범위 내 특정 level 로그만 timestamp 오름차순으로 조회한다. */
    @Query("SELECT l FROM LogEntity l WHERE l.id.appName = :appName " +
            "AND l.id.timestamp BETWEEN :start AND :end " +
            "AND l.level = :level " +
            "ORDER BY l.id.timestamp ASC")
    List<LogEntity> findStreamByLevel(
            @Param("appName") String appName,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("level") String level);

}