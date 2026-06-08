package com.apm.observatory.aipipeline.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * erosion_trend_slopes 테이블 매핑. 자원·응답 추세 기울기를 저장한다.
 * 감지 시 analysis_id로 결과와 연결되고, 미감지 시 null이다.
 */
@Entity
@Table(name = "erosion_trend_slopes")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErosionTrendSlopeEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "analysis_id")
    private String analysisId;

    @Column(name = "app_name", nullable = false)
    private String appName;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "resource_slope", nullable = false)
    private double resourceSlope;

    @Column(name = "response_slope", nullable = false)
    private double responseSlope;

}
