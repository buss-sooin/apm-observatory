package com.apm.observatory.aipipeline.ai.adapter;

import com.apm.observatory.aipipeline.ai.entity.*;
import com.apm.observatory.aipipeline.ai.model.AiCallResult;
import com.apm.observatory.aipipeline.ai.port.AiAnalysisResultPort;
import com.apm.observatory.aipipeline.ai.repository.*;
import com.apm.observatory.aipipeline.analysis.model.CollapseIncident;
import com.apm.observatory.aipipeline.analysis.model.ErosionIncident;
import com.apm.observatory.aipipeline.analysis.model.ExternalImpactIncident;
import com.apm.observatory.aipipeline.analysis.status.AnalysisType;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.ExternalSpanSnapshot;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.MetricsSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.SpanSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@link AiAnalysisResultPort} 구현. AI 호출이 성공하면 분석 결과·raw 응답·근거
 * (span·metrics evidence)를 함께 저장하고, 실패하면 raw 응답만 남긴다.
 */
@Component
@RequiredArgsConstructor
public class AiAnalysisResultAdapter implements AiAnalysisResultPort {

    private final AiAnalysisResultRepository repository;
    private final AiRawResponseRepository rawResponseRepository;
    private final ErosionTrendSlopeRepository erosionTrendSlopeRepository;
    private final AiAnalysisSpanEvidenceRepository spanEvidenceRepository;
    private final AiAnalysisMetricsEvidenceRepository metricsEvidenceRepository;

    @Override
    public void saveCollapseResult(AiCallResult aiCallResult, CollapseIncident incident) {
        if (aiCallResult.isSuccess()) {
            String analysisId = UUID.randomUUID().toString();
            repository.save(buildAnalysisEntity(analysisId, aiCallResult, incident.appName(),
                    AnalysisType.COLLAPSE, incident.analysisStart(), incident.analysisEnd()));
            rawResponseRepository.save(buildRawEntity(
                    aiCallResult, incident.appName(), AnalysisType.COLLAPSE, analysisId));
            saveSpanEvidence(analysisId, incident.recentSpans());
            saveMetricsEvidence(analysisId, incident.recentMetrics());
        } else {
            rawResponseRepository.save(buildRawEntity(
                    aiCallResult, incident.appName(), AnalysisType.COLLAPSE, null));
        }
    }

    @Override
    public void saveErosionResult(AiCallResult aiCallResult, ErosionIncident incident) {
        if (aiCallResult.isSuccess()) {
            String analysisId = UUID.randomUUID().toString();
            repository.save(buildAnalysisEntity(analysisId, aiCallResult, incident.appName(),
                    AnalysisType.EROSION, incident.analysisStart(), incident.analysisEnd()));
            rawResponseRepository.save(buildRawEntity(
                    aiCallResult, incident.appName(), AnalysisType.EROSION, analysisId));
            erosionTrendSlopeRepository.save(ErosionTrendSlopeEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .analysisId(analysisId)
                    .appName(incident.appName())
                    .timestamp(Instant.now())
                    .resourceSlope(incident.resourceSlope())
                    .responseSlope(incident.responseSlope())
                    .build());
        } else {
            rawResponseRepository.save(buildRawEntity(
                    aiCallResult, incident.appName(), AnalysisType.EROSION, null));
        }
    }

    @Override
    public void saveExternalImpactResult(AiCallResult aiCallResult, ExternalImpactIncident incident) {
        if (aiCallResult.isSuccess()) {
            String analysisId = UUID.randomUUID().toString();
            repository.save(buildAnalysisEntity(analysisId, aiCallResult, incident.appName(),
                    AnalysisType.EXTERNAL_IMPACT, incident.analysisStart(), incident.analysisEnd()));
            rawResponseRepository.save(buildRawEntity(
                    aiCallResult, incident.appName(), AnalysisType.EXTERNAL_IMPACT, analysisId));
            saveExternalSpanEvidence(analysisId, incident.recentExternalSpans());
            saveExternalMetricsEvidence(analysisId, incident.recentMetrics());
        } else {
            rawResponseRepository.save(buildRawEntity(
                    aiCallResult, incident.appName(), AnalysisType.EXTERNAL_IMPACT, null));
        }
    }

    // ── evidence 저장 ─────────────────────────────────────────────

    private void saveSpanEvidence(String analysisId,
                                  List<SpanSnapshot> spans) {
        spans.forEach(span ->
                spanEvidenceRepository.save(AiAnalysisSpanEvidenceEntity.builder()
                        .id(UUID.randomUUID().toString())
                        .analysisId(analysisId)
                        .spanId(span.spanId())
                        .build()));
    }

    private void saveExternalSpanEvidence(String analysisId, List<ExternalSpanSnapshot> spans) {
        spans.forEach(span ->
                spanEvidenceRepository.save(AiAnalysisSpanEvidenceEntity.builder()
                        .id(UUID.randomUUID().toString())
                        .analysisId(analysisId)
                        .spanId(span.spanId())
                        .build()));
    }

    private void saveMetricsEvidence(String analysisId,
                                     List<com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.MetricsSnapshot> metrics) {
        metrics.forEach(metric ->
                metricsEvidenceRepository.save(AiAnalysisMetricsEvidenceEntity.builder()
                        .id(UUID.randomUUID().toString())
                        .analysisId(analysisId)
                        .metricTimestamp(metric.timestamp())
                        .metricAppName(metric.appName())
                        .build()));
    }

    private void saveExternalMetricsEvidence(String analysisId, List<MetricsSnapshot> metrics) {
        metrics.forEach(metric ->
                metricsEvidenceRepository.save(AiAnalysisMetricsEvidenceEntity.builder()
                        .id(UUID.randomUUID().toString())
                        .analysisId(analysisId)
                        .metricTimestamp(metric.timestamp())
                        .metricAppName(metric.appName())
                        .build()));
    }

    // ── 공통 builder ──────────────────────────────────────────────

    private AiAnalysisResultEntity buildAnalysisEntity(
            String analysisId, AiCallResult aiCallResult,
            String appName, AnalysisType fusionCriteria,
            Instant analysisStart, Instant analysisEnd) {
        return AiAnalysisResultEntity.builder()
                .id(analysisId)
                .timestamp(Instant.now())
                .appName(appName)
                .fusionCriteria(fusionCriteria.getValue())
                .patternType(aiCallResult.result().analysisType().getValue())
                .spanType(aiCallResult.result().spanType())
                .severity(aiCallResult.result().severity())
                .aiSummary(aiCallResult.result().aiSummary())
                .rootCause(aiCallResult.result().rootCause())
                .recommendation(aiCallResult.result().recommendation())
                .analysisStartTime(analysisStart)
                .analysisEndTime(analysisEnd)
                .build();
    }

    private AiRawResponseEntity buildRawEntity(
            AiCallResult aiCallResult, String appName,
            AnalysisType fusionCriteria, String analysisId) {
        return AiRawResponseEntity.builder()
                .id(UUID.randomUUID().toString())
                .appName(appName)
                .fusionCriteria(fusionCriteria.getValue())
                .rawResponse(aiCallResult.rawResponse())
                .parseStatus(aiCallResult.parseStatus().name())
                .errorMessage(aiCallResult.errorMessage())
                .analysisId(analysisId)
                .timestamp(Instant.now())
                .build();
    }

}