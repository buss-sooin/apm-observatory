package com.apm.observatory.aipipeline.ai.exception;

/**
 * Ollama가 기대에 못 미치는 응답을 돌려준 경우의 최상위 예외. 하위 클래스로
 * 케이스를 구분하되, 한 {@code catch}로 묶어 처리할 수 있게 한다.
 */
public abstract class OllamaResponseException extends RuntimeException {

    public OllamaResponseException(String message) {
        super(message);
    }

    public OllamaResponseException(String message, Throwable cause) {
        super(message, cause);
    }

}