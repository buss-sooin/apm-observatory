package com.apm.observatory.aipipeline.ai.entity;

import com.apm.observatory.aipipeline.analysis.status.AnalysisType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** ai_analysis_results 테이블 매핑. AI 분석 결과를 저장한다. */
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

    @Column(name = "fusion_criteria")
    private Integer fusionCriteria;

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

    /** fusion_criteria(int)를 {@link AnalysisType}으로 변환한다. */
    public AnalysisType fusionCriteriaType() {
        return AnalysisType.from(fusionCriteria);
    }

    /** pattern_type(int)을 {@link AnalysisType}으로 변환한다. */
    public AnalysisType patternAnalysisType() {
        return AnalysisType.from(patternType);
    }

}
