package com.apm.observatory.aipipeline.ai.exception;

/** JSON 파싱은 됐으나 필수 필드가 누락된 경우. {@code ParseStatus.VALIDATION_FAILED}에 대응한다. */
public class OllamaValidationException extends OllamaResponseException {

    public OllamaValidationException(String message) {
        super(message);
    }

}