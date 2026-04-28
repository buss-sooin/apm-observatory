package com.apm.observatory.aipipeline.ai.exception;

// 의도: Ollama가 JSON 형식이 아닌 응답을 돌려준 경우
// JsonProcessingException을 wrap하여 cause 보존
// ParseStatus.JSON_PARSE_FAILED 에 대응
public class OllamaParseException extends OllamaResponseException {

    public OllamaParseException(String message, Throwable cause) {
        super(message, cause);
    }

}