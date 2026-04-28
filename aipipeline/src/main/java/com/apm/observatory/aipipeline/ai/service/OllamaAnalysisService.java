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
                // 의도: Ollama가 JSON 형식이 아닌 응답을 돌려준 경우
                // cause 보존하여 근본 원인 추적 가능하게 wrap
                throw new OllamaParseException("Ollama JSON 파싱 실패: " + e.getMessage(), e);
            }

            // 의도: 파싱은 성공했으나 필수 필드 누락인 경우
            // 예외로 던져서 catch (OllamaResponseException e) 흐름으로 통일
            if (!result.isValid()) {
                throw new OllamaValidationException(
                        "Ollama 응답 필드 검증 실패 - 필수 필드 null/blank: " + rawResponse
                );
            }

            return new AiCallResult(ParseStatus.SUCCESS, rawResponse, result, null);

        } catch (OllamaResponseException e) {
            // 의도: 수신측(Ollama) 책임의 모든 예외를 여기서 수신
            // raw_response는 가능한 한 보존 (파싱 실패여도 rawResponse는 있음)
            log.error("Ollama 응답 처리 실패 [{}]: {}", e.getClass().getSimpleName(), e.getMessage());
            ParseStatus status = (e instanceof OllamaParseException)
                    ? ParseStatus.JSON_PARSE_FAILED
                    : ParseStatus.VALIDATION_FAILED;
            return new AiCallResult(status, rawResponse, null, e.getMessage());
        }
    }

}