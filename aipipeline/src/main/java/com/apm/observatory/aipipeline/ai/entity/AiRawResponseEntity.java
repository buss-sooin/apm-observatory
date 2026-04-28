package com.apm.observatory.aipipeline.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

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

    // 의도: Ollama가 뱉은 날것 응답 그대로 저장
    // AI_ERROR 시 null 가능 (Ollama 서버 자체가 응답 못한 경우)
    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    // 의도: ParseStatus enum의 name()으로 저장
    // 상태 추가 시 enum만 수정하면 됨
    @Column(name = "parse_status")
    private String parseStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // 의도: SUCCESS 시 ai_analysis_results.id 연결 → 두 테이블 역추적 가능
    // 실패 시 null
    @Column(name = "analysis_id")
    private String analysisId;

    @Column(name = "timestamp")
    private Instant timestamp;

}