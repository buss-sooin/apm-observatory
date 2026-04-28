package com.apm.observatory.aipipeline.ai.model;

public enum ParseStatus {

    // Ollama 응답 파싱 + isValid() 검증까지 통과
    SUCCESS,

    // Ollama가 JSON 형식이 아닌 응답을 돌려줌 (자연어 응답 등)
    JSON_PARSE_FAILED,

    // JSON 파싱은 됐으나 필수 필드 null/blank
    VALIDATION_FAILED

}