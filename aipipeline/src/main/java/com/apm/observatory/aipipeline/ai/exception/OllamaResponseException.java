package com.apm.observatory.aipipeline.ai.exception;

// 의도: 수신측(Ollama)이 기대에 못 미치는 응답을 돌려준 경우의 최상위 예외
// 하위 클래스로 케이스를 구분하여 catch (OllamaResponseException e) 하나로 묶어서 처리 가능
// 나중에 케이스 추가 시 하위 클래스만 추가하면 됨
public abstract class OllamaResponseException extends RuntimeException {

    public OllamaResponseException(String message) {
        super(message);
    }

    public OllamaResponseException(String message, Throwable cause) {
        super(message, cause);
    }

}