package com.apm.observatory.aipipeline.analysis.status;

import com.apm.observatory.aipipeline.ai.exception.OllamaValidationException;

import java.util.Arrays;

// 의도: fusion_criteria(백엔드 감지 전략)와 pattern_type(AI 분석 패턴) 양쪽에서 공유하는 enum
// 같은 값 체계(1,2,3)를 쓰지만 의미가 다른 두 컬럼 — 변수명/컬럼명으로 구분
// DB에는 value(int)로 저장, API 응답에는 name(String)으로 반환
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

    // AI 응답 int → enum 변환
    // 알 수 없는 값은 OllamaValidationException — isValid() 이후 호출되므로
    // 정상 흐름에서는 발생하지 않아야 함
    public static AnalysisType from(int value) {
        return Arrays.stream(values())
                .filter(t -> t.value == value)
                .findFirst()
                .orElseThrow(() -> new OllamaValidationException(
                        "알 수 없는 pattern_type 값: " + value));
    }

}