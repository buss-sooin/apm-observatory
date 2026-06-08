package com.apm.observatory.aipipeline.ai.service;

import com.apm.observatory.aipipeline.ai.exception.OllamaParseException;
import com.apm.observatory.aipipeline.ai.exception.OllamaResponseException;
import com.apm.observatory.aipipeline.ai.exception.OllamaValidationException;
import com.apm.observatory.aipipeline.ai.model.AiAnalysisResult;
import com.apm.observatory.aipipeline.ai.model.AiCallResult;
import com.apm.observatory.aipipeline.ai.model.ParseStatus;
import com.apm.observatory.aipipeline.analysis.model.CollapseIncident;
import com.apm.observatory.aipipeline.analysis.model.ErosionIncident;
import com.apm.observatory.aipipeline.analysis.model.ExternalImpactIncident;
import com.apm.observatory.aipipeline.analysis.prompt.AnalysisPromptBuilderProvider;
import com.apm.observatory.aipipeline.analysis.prompt.PromptBuildStrategy;
import com.apm.observatory.aipipeline.config.AiPipelineConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Ollama에 분석을 요청하고 응답을 파싱·검증하는 서비스. 사건 종류별 analyze를
 * 제공하며, 내부적으로 프롬프트 전략을 만들어 한 경로(call)로 호출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaAnalysisService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AiPipelineConfig aiPipelineConfig;

    public AiCallResult analyze(CollapseIncident incident) {
        return call(AnalysisPromptBuilderProvider.of(incident, aiPipelineConfig.prompt()));
    }

    public AiCallResult analyze(ErosionIncident incident) {
        return call(AnalysisPromptBuilderProvider.of(incident, aiPipelineConfig.prompt()));
    }

    public AiCallResult analyze(ExternalImpactIncident incident) {
        return call(AnalysisPromptBuilderProvider.of(incident, aiPipelineConfig.prompt()));
    }

    /**
     * 프롬프트를 만들어 Ollama를 호출하고 결과를 {@link AiCallResult}로 돌려준다.
     * JSON 파싱 실패와 필드 검증 실패는 모두 {@link OllamaResponseException}으로
     * 통일해 한 catch에서 처리하며, 실패해도 받은 raw 응답은 보존한다.
     */
    private AiCallResult call(PromptBuildStrategy strategy) {
        String rawResponse = null;
        try {
            rawResponse = chatClient.prompt()
                    .system(strategy.buildSystemPrompt())
                    .user(strategy.buildUserPrompt())
                    .call()
                    .content();

            log.info("Ollama 응답: {}", rawResponse);

            AiAnalysisResult result;
            try {
                result = objectMapper.readValue(rawResponse, AiAnalysisResult.class);
            } catch (JsonProcessingException e) {
                throw new OllamaParseException("Ollama JSON 파싱 실패: " + e.getMessage(), e);
            }

            if (!result.isValid()) {
                throw new OllamaValidationException(
                        "Ollama 응답 필드 검증 실패 - 필수 필드 null/blank: " + rawResponse
                );
            }

            return new AiCallResult(ParseStatus.SUCCESS, rawResponse, result, null);

        } catch (OllamaResponseException e) {
            log.error("Ollama 응답 처리 실패 [{}]: {}", e.getClass().getSimpleName(), e.getMessage());
            ParseStatus status = (e instanceof OllamaParseException)
                    ? ParseStatus.JSON_PARSE_FAILED
                    : ParseStatus.VALIDATION_FAILED;
            return new AiCallResult(status, rawResponse, null, e.getMessage());
        }
    }

}