package com.apm.observatory.aipipeline.config;

import com.apm.observatory.aipipeline.ai.exception.InvalidPromptException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** aipipeline.* 설정을 바인딩하는 프로퍼티. 스케줄 주기·임계값·분석 윈도우·프롬프트를 묶는다. */
@ConfigurationProperties(prefix = "aipipeline")
public record AiPipelineConfig(Scheduler scheduler, Threshold threshold, Window window, Prompt prompt) {

    public record Scheduler(int intervalMinutes) {}

    /** 이상·추세 판정 임계값 묶음. */
    public record Threshold(
            double spikeMultiplier,
            double slopeMinPositive,
            double externalRatioMultiplier,
            double cpuThreshold,
            double memoryThreshold
    ) {}

    /** 분석 시간 윈도우(분): 최근 구간·baseline·erosion. */
    public record Window(int recentMinutes, int baselineMinutes, int erosionMinutes) {}

    /**
     * 시스템 프롬프트 구성 요소를 역할별로 나눠 yml에서 관리한다. role·language·
     * additionalInstructions는 표현·강도를 조절하는 자유 영역이고, responseFormat·
     * fieldConstraints는 AiAnalysisResult/DB 계약과 묶인 고정 영역이다.
     */
    public record Prompt(
            String role,
            String language,
            String additionalInstructions,
            String responseFormat,
            String fieldConstraints
    ) {

        /** response-format이 유효한 JSON인지 검증한다. 앱 시작 시 한 번 호출한다. */
        public void validateResponseFormat(ObjectMapper objectMapper) throws InvalidPromptException {
            try {
                objectMapper.readTree(responseFormat);
            } catch (Exception e) {
                throw new InvalidPromptException(
                        "aipipeline.prompt.response-format 이 유효한 JSON이 아닙니다: " + e.getMessage(), e
                );
            }
        }

    }

}
