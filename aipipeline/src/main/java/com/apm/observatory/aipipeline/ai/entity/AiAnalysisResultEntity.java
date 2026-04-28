package com.apm.observatory.aipipeline.ai.entity;

import com.apm.observatory.aipipeline.analysis.status.AnalysisType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ai_analysis_results")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalysisResultEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "timestamp")
    private Instant timestamp;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "app_name")
    private String appName;

    // DB에는 int(1,2,3)로 저장 — AnalysisType.getValue()로 저장
    // 읽을 때는 fusionCriteriaType()으로 AnalysisType 변환
    @Column(name = "fusion_criteria")
    private Integer fusionCriteria;

    // AI가 판단한 패턴 유형 — DB에는 int로 저장
    // 읽을 때는 patternAnalysisType()으로 AnalysisType 변환
    @Column(name = "pattern_type")
    private Integer patternType;

    @Column(name = "span_type")
    private String spanType;

    @Column(name = "severity")
    private String severity;

    @Column(name = "ai_summary")
    private String aiSummary;

    @Column(name = "root_cause")
    private String rootCause;

    @Column(name = "recommendation")
    private String recommendation;

    @Column(name = "analysis_start_time")
    private Instant analysisStartTime;

    @Column(name = "analysis_end_time")
    private Instant analysisEndTime;

    public AnalysisType fusionCriteriaType() {
        return AnalysisType.from(fusionCriteria);
    }

    public AnalysisType patternAnalysisType() {
        return AnalysisType.from(patternType);
    }

}