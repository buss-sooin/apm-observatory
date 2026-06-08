package com.apm.observatory.aipipeline.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * ai_raw_responses 테이블 매핑. Ollama 날것 응답과 파싱 상태를 저장한다.
 * 성공 시 analysis_id로 ai_analysis_results와 연결되고, 실패 시 null이다.
 */
@Entity
@Table(name = "ai_raw_responses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiRawResponseEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "app_name")
    private String appName;

    @Column(name = "fusion_criteria")
    private Integer fusionCriteria;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "parse_status")
    private String parseStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "analysis_id")
    private String analysisId;

    @Column(name = "timestamp")
    private Instant timestamp;

}
