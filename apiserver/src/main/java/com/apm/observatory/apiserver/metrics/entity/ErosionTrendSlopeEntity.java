package com.apm.observatory.apiserver.metrics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// 의도: erosion_trend_slopes 테이블 읽기 전용 매핑
// summarizePerformance()에서 최근 slope 조회용
@Entity
@Table(name = "erosion_trend_slopes")
public class ErosionTrendSlopeEntity {

    @Id
    private String id;

    @Column(name = "analysis_id")
    private String analysisId;

    @Column(name = "app_name")
    private String appName;

    @Column(name = "timestamp")
    private Instant timestamp;

    @Column(name = "resource_slope")
    private double resourceSlope;

    @Column(name = "response_slope")
    private double responseSlope;

    protected ErosionTrendSlopeEntity() {}

    public String getId() { return id; }
    public String getAnalysisId() { return analysisId; }
    public String getAppName() { return appName; }
    public Instant getTimestamp() { return timestamp; }
    public double getResourceSlope() { return resourceSlope; }
    public double getResponseSlope() { return responseSlope; }

}