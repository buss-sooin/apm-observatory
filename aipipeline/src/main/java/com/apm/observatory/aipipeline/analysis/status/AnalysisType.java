package com.apm.observatory.aipipeline.analysis.status;

import com.apm.observatory.aipipeline.ai.exception.OllamaValidationException;

import java.util.Arrays;

/**
 * 분석 종류. 백엔드 감지 전략(fusion_criteria)과 AI 분석 패턴(pattern_type)
 * 양쪽에서 같은 값 체계(1·2·3)로 공유한다. 의미가 다른 두 컬럼은 변수명·컬럼명으로
 * 구분한다. DB에는 value(int)로 저장하고 API 응답에는 name(String)으로 내보낸다.
 */
public enum AnalysisType {

    COLLAPSE(1),
    EROSION(2),
    EXTERNAL_IMPACT(3);

    private final int value;

    AnalysisType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * AI 응답 int 값을 enum으로 옮긴다. 알 수 없는 값이면
     * {@link OllamaValidationException}을 던지나, isValid() 통과 이후 호출되므로
     * 정상 흐름에서는 발생하지 않는다.
     */
    public static AnalysisType from(int value) {
        return Arrays.stream(values())
                .filter(t -> t.value == value)
                .findFirst()
                .orElseThrow(() -> new OllamaValidationException(
                        "알 수 없는 pattern_type 값: " + value));
    }

}
