package com.apm.observatory.apiserver.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * ai_analysis_results 테이블 읽기 전용 매핑. aipipeline이 저장하고 apiserver는 조회만 한다.
 * fusion_criteria·pattern_type은 int로 저장돼 Adapter에서 AnalysisType으로 변환된다.
 */
@Entity
@Table(name = "ai_analysis_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiAnalysisResultEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "timestamp")
    private Instant timestamp;

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

}