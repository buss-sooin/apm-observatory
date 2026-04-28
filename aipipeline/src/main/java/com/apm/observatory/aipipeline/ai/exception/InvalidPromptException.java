package com.apm.observatory.aipipeline.ai.exception;

// 의도: response-format 필드가 유효한 JSON이 아닌 경우
// Checked Exception — 개발자가 yml을 잘못 작성한 경우로 반드시 인지하고 대응해야 함
// 앱 시작 시점(PromptConfig 초기화)에 한 번만 검증
public class InvalidPromptException extends Exception {

    public InvalidPromptException(String message, Throwable cause) {
        super(message, cause);
    }

}