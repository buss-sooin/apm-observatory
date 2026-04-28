package com.apm.observatory.aipipeline.ai.exception;

// 의도: JSON 파싱은 성공했으나 isValid() 검증에서 필수 필드가 누락된 경우
// 예외로 던지는 이유: catch (OllamaResponseException e) 처리 흐름을 통일하기 위함
// ParseStatus.VALIDATION_FAILED 에 대응
public class OllamaValidationException extends OllamaResponseException {

    public OllamaValidationException(String message) {
        super(message);
    }

}