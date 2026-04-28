package com.apm.observatory.aipipeline.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

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

    // 의도: DETECTED일 때는 ai_analysis_results와 연결 → 역추적 가능
    // NOT_DETECTED일 때는 null → slope만 독립적으로 저장
    @Column(name = "analysis_id")
    private String analysisId;

    @Column(name = "app_name", nullable = false)
    private String appName;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    // 의도: CPU 기반 자원 추세 기울기
    // 양수 = 자원 사용량 상승 중, slope_min_positive 초과 시 RISING 판단
    @Column(name = "resource_slope", nullable = false)
    private double resourceSlope;

    // 의도: 응답시간 기반 추세 기울기
    // 양수 = 응답시간 상승 중, slope_min_positive 초과 시 RISING 판단
    @Column(name = "response_slope", nullable = false)
    private double responseSlope;

}