package com.apm.observatory.aipipeline.ai.entity;

import jakarta.persistence.*;
import lombok.*;

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

    // 의도: AI 분석 결과와 연결 — "이 분석이 어떤 Span을 근거로 했는가" 역추적
    @Column(name = "analysis_id", nullable = false)
    private String analysisId;

    @Column(name = "span_id", nullable = false)
    private String spanId;

}