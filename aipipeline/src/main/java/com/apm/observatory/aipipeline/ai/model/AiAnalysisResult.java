package com.apm.observatory.aipipeline.ai.model;

import com.apm.observatory.aipipeline.analysis.status.AnalysisType;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AiAnalysisResult(
        @JsonProperty("pattern_type") int patternType,
        @JsonProperty("span_type") String spanType,
        @JsonProperty("root_cause") String rootCause,
        @JsonProperty("ai_summary") String aiSummary,
        @JsonProperty("recommendation") String recommendation,
        @JsonProperty("severity") String severity
) {

    // 의도: 파싱은 성공했지만 필수 필드가 누락된 경우를 판단하는 책임을 record 자신이 가짐
    // patternType은 int 기본값 0으로 파싱되므로 AnalysisType.from()으로 유효성 검증 포함
    public boolean isValid() {
        if (!isKnownPatternType()) return false;
        return isNotBlank(spanType)
                && isNotBlank(rootCause)
                && isNotBlank(aiSummary)
                && isNotBlank(recommendation)
                && isNotBlank(severity);
    }

    // pattern_type이 알려진 값(1,2,3)인지 검증
    // AI가 범위 밖 값을 뱉으면 false → OllamaValidationException 흐름으로
    public boolean isKnownPatternType() {
        try {
            AnalysisType.from(patternType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 의도: 검증 통과 후 AnalysisType으로 변환
    // isValid() 통과 후에만 호출해야 함
    public AnalysisType analysisType() {
        return AnalysisType.from(patternType);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

}