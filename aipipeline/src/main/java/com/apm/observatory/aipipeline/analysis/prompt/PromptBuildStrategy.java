package com.apm.observatory.aipipeline.analysis.prompt;

import com.apm.observatory.aipipeline.config.AiPipelineConfig.Prompt;

public interface PromptBuildStrategy {

    default String build() {
        return buildSystemPrompt() + "\n" + buildUserPrompt();
    }

    // 의도: default 메서드 구조 유지 — 공통 행위는 인터페이스가 책임
    // 내부 구현만 하드코딩 리터럴 → Prompt 주입값 조합으로 교체
    // additionalInstructions는 null/blank면 건너뜀
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

    // 의도: 각 전략 클래스가 Prompt를 제공하는 책임을 가짐
    // interface는 조합 방식을 정의, 전략은 재료를 제공
    Prompt getPrompt();

    String buildUserPrompt();

}