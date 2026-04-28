package com.apm.observatory.apiserver.config.adapter;

import com.apm.observatory.apiserver.config.entity.BusinessCycleEntity;
import com.apm.observatory.apiserver.config.repository.BusinessCycleRepository;
import com.apm.observatory.apiserver.config.entity.ThresholdConfigEntity;
import com.apm.observatory.apiserver.config.repository.ThresholdConfigRepository;
import com.apm.observatory.apiserver.config.model.ConfigModel.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ConfigAdapter {

    private final ThresholdConfigRepository repository;
    private final BusinessCycleRepository businessCycleRepository;

    public ThresholdResponse upsertThreshold(ThresholdRequest request) {
        Optional<ThresholdConfigEntity> existing = repository.findByAppName(request.appName());

        ThresholdConfigEntity entity = existing
                .map(e -> e.updateWith(
                        request.cpuThreshold(),
                        request.memoryThreshold(),
                        request.diskIoThreshold(),
                        request.spanDurationMultiplier(),
                        request.externalRatioMultiplier(),
                        request.slopeMinPositive()
                ))
                .orElseGet(() -> new ThresholdConfigEntity(
                        UUID.randomUUID().toString(),
                        request.appName(),
                        request.cpuThreshold() != null ? request.cpuThreshold() : 80.0,
                        request.memoryThreshold() != null ? request.memoryThreshold() : 80.0,
                        request.diskIoThreshold() != null ? request.diskIoThreshold() : 100000000L,
                        request.spanDurationMultiplier() != null ? request.spanDurationMultiplier() : 3.0,
                        request.externalRatioMultiplier() != null ? request.externalRatioMultiplier() : 3.0,
                        request.slopeMinPositive() != null ? request.slopeMinPositive() : 0.01
                ));

        ThresholdConfigEntity saved = repository.save(entity);

        return new ThresholdResponse(
                saved.getAppName(),
                saved.getCpuThreshold(),
                saved.getMemoryThreshold(),
                saved.getDiskIoThreshold(),
                saved.getSpanDurationMultiplier(),
                saved.getExternalRatioMultiplier(),
                saved.getSlopeMinPositive()
        );
    }

    // 의도: upsert — app_name 없으면 신규 생성, 있으면 수정
    // null이면 기존값 유지
    public BusinessCycleResponse upsertBusinessCycle(BusinessCycleRequest request) {
        Optional<BusinessCycleEntity> existing = businessCycleRepository.findByAppName(request.appName());

        BusinessCycleEntity entity = existing
                .map(e -> e.updateWith(
                        request.cycleStart(),
                        request.cycleEnd(),
                        request.peakStart(),
                        request.peakEnd()
                ))
                .orElseGet(() -> BusinessCycleEntity.builder()
                        .id(UUID.randomUUID().toString())
                        .appName(request.appName())
                        .cycleStart(request.cycleStart())
                        .cycleEnd(request.cycleEnd())
                        .peakStart(request.peakStart())
                        .peakEnd(request.peakEnd())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build());

        BusinessCycleEntity saved = businessCycleRepository.save(entity);

        return new BusinessCycleResponse(
                saved.getAppName(),
                saved.getCycleStart(),
                saved.getCycleEnd(),
                saved.getPeakStart(),
                saved.getPeakEnd()
        );
    }

    // 의도: 삭제 시 aipipeline이 baseline fallback으로 돌아감
    // "null로 upsert"가 아닌 명시적 삭제로 의도를 명확히 함
    public void deleteBusinessCycle(String appName) {
        businessCycleRepository.deleteByAppName(appName);
    }

}