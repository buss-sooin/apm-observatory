package com.apm.observatory.aipipeline.ai.entity;

import jakarta.persistence.*;
import lombok.*;

/** ai_analysis_span_evidence 테이블 매핑. 분석이 근거로 삼은 span을 결과(analysis_id)와 연결해 남긴다. */
@Entity
@Table(name = "ai_analysis_span_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiAnalysisSpanEvidenceEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "analysis_id", nullable = false)
    private String analysisId;

    @Column(name = "span_id", nullable = false)
    private String spanId;

}