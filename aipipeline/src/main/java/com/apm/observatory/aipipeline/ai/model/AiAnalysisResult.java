package com.apm.observatory.aipipeline.ai.model;

import com.apm.observatory.aipipeline.analysis.status.AnalysisType;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ollama 응답 JSON을 매핑하는 record. 파싱 후 필수 필드 충족 여부를 스스로
 * 판단하는 책임도 가진다.
 */
public record AiAnalysisResult(
        @JsonProperty("pattern_type") int patternType,
        @JsonProperty("span_type") String spanType,
        @JsonProperty("root_cause") String rootCause,
        @JsonProperty("ai_summary") String aiSummary,
        @JsonProperty("recommendation") String recommendation,
        @JsonProperty("severity") String severity
) {

    /** 필수 필드가 모두 채워졌고 pattern_type이 알려진 값인지 검증한다. */
    public boolean isValid() {
        if (!isKnownPatternType()) return false;
        return isNotBlank(spanType)
                && isNotBlank(rootCause)
                && isNotBlank(aiSummary)
                && isNotBlank(recommendation)
                && isNotBlank(severity);
    }

    /** pattern_type이 알려진 값(1·2·3)인지. 범위 밖이면 false. */
    public boolean isKnownPatternType() {
        try {
            AnalysisType.from(patternType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** pattern_type을 {@link AnalysisType}으로 변환한다. isValid() 통과 후에만 호출한다. */
    public AnalysisType analysisType() {
        return AnalysisType.from(patternType);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

}
