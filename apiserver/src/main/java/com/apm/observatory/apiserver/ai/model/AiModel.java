package com.apm.observatory.apiserver.ai.model;

import java.time.Instant;
import java.util.Arrays;

public class AiModel {

    // 의도: fusion_criteria(백엔드 감지 전략)와 pattern_type(AI 분석 패턴) 양쪽에서 공유
    // DB에는 int로 저장, API 응답에는 name()으로 반환
    public enum AnalysisType {
        COLLAPSE(1), EROSION(2), EXTERNAL_IMPACT(3);

        private final int value;
        AnalysisType(int value) { this.value = value; }

        public static AnalysisType from(int value) {
            return Arrays.stream(values())
                    .filter(t -> t.value == value)
                    .findFirst()
                    .orElse(null);  // apiserver는 읽기 전용 — 알 수 없는 값은 null로 처리
        }
    }

    // GET /ai/results?app_name= 목록 응답
    public record AiResultSummary(
            String id,
            String appName,
            AnalysisType fusionCriteria,   // 백엔드 감지 전략
            AnalysisType patternType,       // AI 분석 패턴
            String spanType,
            String severity,
            String aiSummary,
            String rootCause,
            String recommendation,
            Instant analysisStartTime,
            Instant analysisEndTime,
            Instant timestamp
    ) {}

}