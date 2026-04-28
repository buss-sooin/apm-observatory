package com.apm.observatory.aipipeline.prompt;

import com.apm.observatory.aipipeline.analysis.model.CollapseIncident;
import com.apm.observatory.aipipeline.analysis.model.ErosionDataPoint;
import com.apm.observatory.aipipeline.analysis.model.ErosionIncident;
import com.apm.observatory.aipipeline.analysis.model.ExternalImpactIncident;
import com.apm.observatory.aipipeline.analysis.prompt.AnalysisPromptBuilderProvider;
import com.apm.observatory.aipipeline.analysis.prompt.PromptBuildStrategy;
import com.apm.observatory.aipipeline.config.AiPipelineConfig.Prompt;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.ExternalSpanSnapshot;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.MetricsSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.SpanSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AI가 장애를 정확히 분석하려면 충분한 컨텍스트가 프롬프트에 담겨야 한다")
class PromptBuildStrategyTest {

    private static final Instant NOW = Instant.now();
    private static final Instant START = NOW.minusSeconds(60);

    // 의도: 테스트용 Prompt 픽스처
    // yml 주입 없이 테스트에서 직접 생성 — 고정 영역(responseFormat, fieldConstraints)은
    // 실제 운영과 동일한 값을 사용해야 buildSystemPrompt() 검증이 의미 있음
    private static final Prompt TEST_PROMPT = new Prompt(
            "당신은 APM 장애 분석 전문가입니다.",
            "반드시 한국어로만 응답하세요.",
            "반드시 아래 JSON 형식으로만 응답하세요.",
            """
            {
              "pattern_type": 1,
              "span_type": "INTERNAL",
              "root_cause": "근본 원인 한 문장",
              "ai_summary": "요약 한 문장",
              "recommendation": "권고사항 한 문장",
              "severity": "HIGH"
            }
            """,
            "span_type은 반드시 INTERNAL, DB, EXTERNAL 중 하나만 선택하세요."
    );

    // ── 공통 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("모든 프롬프트는 AI 역할과 JSON 응답 형식을 명시한다")
    void 모든_프롬프트는_AI_역할과_응답형식을_명시한다() {
        PromptBuildStrategy strategy = AnalysisPromptBuilderProvider.of(collapseIncident(), TEST_PROMPT);
        assertThat(strategy.buildSystemPrompt())
                .contains("APM 장애 분석 전문가")
                .contains("JSON");
    }

    @Test
    @DisplayName("build()는 AI 역할 정의와 장애 컨텍스트를 하나의 프롬프트로 조합한다")
    void build는_역할정의와_장애컨텍스트를_조합한다() {
        PromptBuildStrategy strategy = AnalysisPromptBuilderProvider.of(collapseIncident(), TEST_PROMPT);
        String result = strategy.build();
        assertThat(result)
                .contains("APM 장애 분석 전문가")
                .contains("test-app");
    }

    // ── Collapse ──────────────────────────────────────────────────

    @Test
    @DisplayName("Collapse 프롬프트는 AI가 자원 급등 정도를 판단할 수 있는 현재값과 기준값을 포함한다")
    void collapse_자원급등_현재값_기준값_포함() {
        PromptBuildStrategy strategy = AnalysisPromptBuilderProvider.of(collapseIncident(), TEST_PROMPT);
        String prompt = strategy.buildUserPrompt();
        assertThat(prompt)
                .contains("85.0")
                .contains("20.0");
    }

    @Test
    @DisplayName("Collapse 프롬프트는 AI가 응답 지연 정도를 판단할 수 있는 현재값과 기준값을 포함한다")
    void collapse_응답지연_현재값_기준값_포함() {
        PromptBuildStrategy strategy = AnalysisPromptBuilderProvider.of(collapseIncident(), TEST_PROMPT);
        String prompt = strategy.buildUserPrompt();
        assertThat(prompt)
                .contains("1500")
                .contains("300");
    }

    @Test
    @DisplayName("Collapse 프롬프트는 분석 대상 앱을 식별할 수 있는 정보를 포함한다")
    void collapse_앱_식별정보_포함() {
        PromptBuildStrategy strategy = AnalysisPromptBuilderProvider.of(collapseIncident(), TEST_PROMPT);
        assertThat(strategy.buildUserPrompt()).contains("test-app");
    }

    // ── Erosion ───────────────────────────────────────────────────

    @Test
    @DisplayName("Erosion 프롬프트는 AI가 자원 상승 추세를 판단할 수 있는 기울기를 포함한다")
    void erosion_자원_기울기_포함() {
        PromptBuildStrategy strategy = AnalysisPromptBuilderProvider.of(erosionIncident(), TEST_PROMPT);
        assertThat(strategy.buildUserPrompt()).contains("0.500");
    }

    @Test
    @DisplayName("Erosion 프롬프트는 AI가 응답시간 상승 추세를 판단할 수 있는 기울기를 포함한다")
    void erosion_응답시간_기울기_포함() {
        PromptBuildStrategy strategy = AnalysisPromptBuilderProvider.of(erosionIncident(), TEST_PROMPT);
        assertThat(strategy.buildUserPrompt()).contains("0.300");
    }

    @Test
    @DisplayName("Erosion 프롬프트는 분석 대상 앱을 식별할 수 있는 정보를 포함한다")
    void erosion_앱_식별정보_포함() {
        PromptBuildStrategy strategy = AnalysisPromptBuilderProvider.of(erosionIncident(), TEST_PROMPT);
        assertThat(strategy.buildUserPrompt()).contains("test-app");
    }

    // ── ExternalImpact ────────────────────────────────────────────

    @Test
    @DisplayName("ExternalImpact 프롬프트는 AI가 외부 API 지연 정도를 판단할 수 있는 현재값과 기준값을 포함한다")
    void external_외부API_현재값_기준값_포함() {
        PromptBuildStrategy strategy = AnalysisPromptBuilderProvider.of(externalImpactIncident(), TEST_PROMPT);
        String prompt = strategy.buildUserPrompt();
        assertThat(prompt)
                .contains("1500")
                .contains("300");
    }

    @Test
    @DisplayName("ExternalImpact 프롬프트는 AI가 어떤 외부 호스트가 문제인지 식별할 수 있는 정보를 포함한다")
    void external_외부호스트_식별정보_포함() {
        PromptBuildStrategy strategy = AnalysisPromptBuilderProvider.of(externalImpactIncident(), TEST_PROMPT);
        assertThat(strategy.buildUserPrompt()).contains("external-api.com");
    }

    @Test
    @DisplayName("ExternalImpact 프롬프트는 분석 대상 앱을 식별할 수 있는 정보를 포함한다")
    void external_앱_식별정보_포함() {
        PromptBuildStrategy strategy = AnalysisPromptBuilderProvider.of(externalImpactIncident(), TEST_PROMPT);
        assertThat(strategy.buildUserPrompt()).contains("test-app");
    }

    // ── 헬퍼 메서드 ───────────────────────────────────────────────

    private CollapseIncident collapseIncident() {
        return new CollapseIncident(
                "test-app", START, NOW, NOW,
                List.of(new PerformanceDataPort.MetricsSnapshot(
                        NOW, "test-app", 85.0, 7000L, 8000L, 0L, 0L)),
                List.of(new SpanSnapshot("span-1", "test-app", "INTERNAL", 1500L, NOW)),
                20.0, 2000.0, 300.0,
                85.0, 7000.0, 1500.0
        );
    }

    private ErosionIncident erosionIncident() {
        return new ErosionIncident(
                "test-app", START, NOW, NOW,
                List.of(new ErosionDataPoint(NOW, 50.0, 4000.0, 500.0)),
                0.5, 0.3
        );
    }

    private ExternalImpactIncident externalImpactIncident() {
        return new ExternalImpactIncident(
                "test-app", START, NOW, NOW,
                List.of(new MetricsSnapshot(NOW, "test-app", 30.0, 3000L, 8000L, 0L, 0L)),
                List.of(new ExternalSpanSnapshot("span-1", "test-app", "external-api.com", 1500L, NOW)),
                300.0,
                1500.0, "external-api.com"
        );
    }

}