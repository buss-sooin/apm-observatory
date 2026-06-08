package com.apm.observatory.aipipeline.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** ai_analysis_metrics_evidence 테이블 매핑. 분석이 근거로 삼은 metrics를 결과(analysis_id)와 연결해 남긴다. metric은 (timestamp, app_name)로 참조한다. */
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

    @Column(name = "analysis_id", nullable = false)
    private String analysisId;

    @Column(name = "metric_timestamp", nullable = false)
    private Instant metricTimestamp;

    @Column(name = "metric_app_name", nullable = false)
    private String metricAppName;

}