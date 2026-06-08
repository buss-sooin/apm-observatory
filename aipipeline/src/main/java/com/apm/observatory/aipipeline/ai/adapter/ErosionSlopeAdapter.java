package com.apm.observatory.aipipeline.ai.adapter;

import com.apm.observatory.aipipeline.ai.entity.ErosionTrendSlopeEntity;
import com.apm.observatory.aipipeline.ai.port.ErosionSlopePort;
import com.apm.observatory.aipipeline.ai.repository.ErosionTrendSlopeRepository;
import com.apm.observatory.aipipeline.analysis.model.SlopeRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * {@link ErosionSlopePort} 구현. 미감지(NOT_DETECTED) 시 slope만 저장하며
 * analysis_id는 null이다. 감지 시에는 {@code AiAnalysisResultAdapter}가
 * analysis_id를 채워 결과와 함께 저장한다.
 */
@Component
@RequiredArgsConstructor
public class ErosionSlopeAdapter implements ErosionSlopePort {

    private final ErosionTrendSlopeRepository repository;

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