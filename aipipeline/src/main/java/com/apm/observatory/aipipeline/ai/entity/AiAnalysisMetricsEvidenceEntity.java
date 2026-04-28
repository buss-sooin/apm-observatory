package com.apm.observatory.aipipeline.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ai_analysis_metrics_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiAnalysisMetricsEvidenceEntity {

    @Id
    @Column(name = "id")
    private String id;

    // 의도: AI 분석 결과와 연결 — "이 분석이 어떤 Metrics를 근거로 했는가" 역추적
    @Column(name = "analysis_id", nullable = false)
    private String analysisId;

    // 의도: metrics 테이블 복합PK (timestamp + app_name) 참조
    @Column(name = "metric_timestamp", nullable = false)
    private Instant metricTimestamp;

    @Column(name = "metric_app_name", nullable = false)
    private String metricAppName;

}