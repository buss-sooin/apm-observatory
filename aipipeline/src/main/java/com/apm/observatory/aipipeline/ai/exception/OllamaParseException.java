package com.apm.observatory.aipipeline.ai.exception;

/** Ollama가 JSON이 아닌 응답을 돌려준 경우. cause를 보존하며 {@code ParseStatus.JSON_PARSE_FAILED}에 대응한다. */
public class OllamaParseException extends OllamaResponseException {

    public OllamaParseException(String message, Throwable cause) {
        super(message, cause);
    }

}