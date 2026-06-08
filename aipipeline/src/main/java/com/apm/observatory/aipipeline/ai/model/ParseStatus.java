package com.apm.observatory.aipipeline.ai.model;

/** Ollama 응답 처리 결과 상태. */
public enum ParseStatus {

    /** 파싱·검증 모두 통과. */
    SUCCESS,

    /** JSON 형식이 아닌 응답. */
    JSON_PARSE_FAILED,

    /** 파싱은 됐으나 필수 필드 누락. */
    VALIDATION_FAILED

}
