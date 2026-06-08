package com.apm.observatory.apiserver.ai.model;

import java.time.Instant;
import java.util.Arrays;

public class AiModel {

    /**
     * fusion_criteria(백엔드 감지 전략)와 pattern_type(AI 분석 패턴) 양쪽에서 공유하는 분석
     * 유형. DB에는 int로 저장하고 API 응답에는 name()으로 반환한다.
     */
    public enum AnalysisType {
        COLLAPSE(1), EROSION(2), EXTERNAL_IMPACT(3);

        private final int value;
        AnalysisType(int value) { this.value = value; }

        /** DB의 int를 enum으로 바꾼다. 알 수 없는 값은 null을 돌려준다(apiserver는 읽기 전용이라 거부하지 않는다). */
        public static AnalysisType from(int value) {
            return Arrays.stream(values())
                    .filter(t -> t.value == value)
                    .findFirst()
                    .orElse(null);
        }
    }

    /** GET /ai/results?app_name= 목록 응답. */
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