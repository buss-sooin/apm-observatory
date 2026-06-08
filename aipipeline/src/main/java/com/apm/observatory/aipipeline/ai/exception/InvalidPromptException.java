package com.apm.observatory.aipipeline.ai.exception;

/**
 * response-format 설정이 유효한 JSON이 아닐 때 던지는 검사 예외(checked).
 * yml 오작성은 개발자가 반드시 인지·대응해야 하므로 checked로 두며, 앱 시작 시
 * 프롬프트 설정 초기화 때 한 번 검증한다.
 */
public class InvalidPromptException extends Exception {

    public InvalidPromptException(String message, Throwable cause) {
        super(message, cause);
    }

}