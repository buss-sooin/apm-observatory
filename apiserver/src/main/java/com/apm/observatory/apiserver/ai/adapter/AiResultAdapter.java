package com.apm.observatory.apiserver.ai.adapter;

import com.apm.observatory.apiserver.ai.entity.AiAnalysisResultEntity;
import com.apm.observatory.apiserver.ai.model.AiModel.AiResultSummary;
import com.apm.observatory.apiserver.ai.model.AiModel.AnalysisType;
import com.apm.observatory.apiserver.ai.repository.AiAnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AiResultAdapter {

    private final AiAnalysisResultRepository repository;

    public List<AiResultSummary> findByAppName(String appName) {
        return repository.findByAppNameOrderByTimestampDesc(appName)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    public Optional<AiResultSummary> findById(String id) {
        return repository.findById(id)
                .map(this::toSummary);
    }

    // 의도: Entity → Response 변환
    // fusionCriteria, patternType 모두 int → AnalysisType enum으로 변환
    // null 방어: AnalysisType.from()이 null 반환 가능 — 알 수 없는 값이 DB에 있는 경우
    private AiResultSummary toSummary(AiAnalysisResultEntity e) {
        return new AiResultSummary(
                e.getId(),
                e.getAppName(),
                AnalysisType.from(e.getFusionCriteria()),
                AnalysisType.from(e.getPatternType()),
                e.getSpanType(),
                e.getSeverity(),
                e.getAiSummary(),
                e.getRootCause(),
                e.getRecommendation(),
                e.getAnalysisStartTime(),
                e.getAnalysisEndTime(),
                e.getTimestamp()
        );
    }

}