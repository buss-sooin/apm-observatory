package com.apm.observatory.apiserver.ai.adapter;

import com.apm.observatory.apiserver.ai.entity.AiAnalysisResultEntity;
import com.apm.observatory.apiserver.ai.model.AiModel.AiResultSummary;
import com.apm.observatory.apiserver.ai.model.AiModel.AnalysisType;
import com.apm.observatory.apiserver.ai.repository.AiAnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * AI 분석 결과 조회와 Entity → Summary 변환을 담당한다. 도메인 판단 없는 단순 조회·변환이라
 * Port 없이 Adapter만 둔다.
 */
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

    /**
     * Entity를 Summary로 변환한다. fusionCriteria·patternType은 int를 AnalysisType으로 바꾸며,
     * DB에 알 수 없는 값이 있으면 AnalysisType.from()이 null을 돌려준다.
     */
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