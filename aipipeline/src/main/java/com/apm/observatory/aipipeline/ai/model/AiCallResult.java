package com.apm.observatory.aipipeline.ai.model;

/**
 * {@code OllamaAnalysisService.call()}의 결과를 하나로 묶은 반환 타입. 호출
 * 상태·raw 응답·파싱 결과·오류 메시지를 담으며, result는 성공일 때만 채워진다.
 * Adapter는 {@link #isSuccess()}로 저장 대상을 분기한다.
 */
public record AiCallResult(
        ParseStatus parseStatus,
        String rawResponse,
        AiAnalysisResult result,
        String errorMessage
) {

    /** parseStatus가 SUCCESS인지. Adapter가 저장 분기에 쓴다. */
    public boolean isSuccess() {
        return parseStatus == ParseStatus.SUCCESS;
    }

}
