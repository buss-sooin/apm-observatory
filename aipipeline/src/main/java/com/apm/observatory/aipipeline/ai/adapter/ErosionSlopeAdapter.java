package com.apm.observatory.aipipeline.ai.adapter;

import com.apm.observatory.aipipeline.ai.entity.ErosionTrendSlopeEntity;
import com.apm.observatory.aipipeline.ai.port.ErosionSlopePort;
import com.apm.observatory.aipipeline.ai.repository.ErosionTrendSlopeRepository;
import com.apm.observatory.aipipeline.analysis.model.SlopeRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ErosionSlopeAdapter implements ErosionSlopePort {

    private final ErosionTrendSlopeRepository repository;

    // 의도: NOT_DETECTED 시 slope만 저장 → analysis_id는 null
    // DETECTED 시에는 AiAnalysisResultAdapter.saveErosionResult()에서 저장
    //   → analysis_id가 채워진 상태로 저장됨
    @Override
    public void save(SlopeRecord slopeRecord) {
        repository.save(ErosionTrendSlopeEntity.builder()
                .id(UUID.randomUUID().toString())
                .analysisId(null)
                .appName(slopeRecord.appName())
                .timestamp(Instant.now())
                .resourceSlope(slopeRecord.resourceSlope())
                .responseSlope(slopeRecord.responseSlope())
                .build());
    }

}