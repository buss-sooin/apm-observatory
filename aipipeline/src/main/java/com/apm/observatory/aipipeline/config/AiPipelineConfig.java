package com.apm.observatory.aipipeline.config;

import com.apm.observatory.aipipeline.ai.exception.InvalidPromptException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aipipeline")
public record AiPipelineConfig(Scheduler scheduler, Threshold threshold, Window window, Prompt prompt) {

    public record Scheduler(int intervalMinutes) {}

    public record Threshold(
            double spikeMultiplier,
            double slopeMinPositive,
            double externalRatioMultiplier,
            double cpuThreshold,
            double memoryThreshold
    ) {}

    public record Window(int recentMinutes, int baselineMinutes, int erosionMinutes) {}

    // 의도: 시스템 프롬프트 구성 요소를 역할별로 분리하여 yml에서 관리
    // 자유 영역(role, language, additionalInstructions): 표현과 강도 조절 가능
    // 고정 영역(responseFormat, fieldConstraints): AiAnalysisResult/DB 계약과 연결되어 변경 불가
    public record Prompt(
            String role,
            String language,
            String additionalInstructions,  // null/blank면 buildSystemPrompt()에서 건너뜀
            String responseFormat,          // JSON 블록 — 앱 시작 시 유효성 검증 대상
            String fieldConstraints         // 허용값 제약
    ) {

        // 의도: response-format이 유효한 JSON인지 앱 시작 시점에 한 번만 검증
        // Checked Exception으로 선언 — 개발자가 반드시 인지하고 대응해야 하는 설정 오류
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