package com.apm.observatory.aipipeline.analysis.prompt;

import com.apm.observatory.aipipeline.config.AiPipelineConfig.Prompt;

/**
 * 분석 종류별 AI 프롬프트 조립 전략. 시스템 프롬프트 조립(공통)은 인터페이스가
 * 맡고, 사용자 프롬프트(사건별 데이터)는 각 전략이 채운다.
 *
 * <p>시스템 프롬프트는 {@link Prompt} 주입값(role·language·responseFormat 등)을
 * 조합해 만들며, additionalInstructions가 비어 있으면 건너뛴다.
 */
public interface PromptBuildStrategy {

    default String build() {
        return buildSystemPrompt() + "\n" + buildUserPrompt();
    }

    default String buildSystemPrompt() {
        Prompt prompt = getPrompt();
        StringBuilder sb = new StringBuilder();
        sb.append(prompt.role()).append("\n");
        sb.append(prompt.language()).append("\n");
        if (prompt.additionalInstructions() != null && !prompt.additionalInstructions().isBlank()) {
            sb.append(prompt.additionalInstructions()).append("\n");
        }
        sb.append(prompt.responseFormat()).append("\n");
        sb.append(prompt.fieldConstraints());
        return sb.toString();
    }

    /** 전략별 {@link Prompt} 설정을 공급한다. */
    Prompt getPrompt();

    /** 사건 데이터로 사용자 프롬프트를 만든다. */
    String buildUserPrompt();

}
