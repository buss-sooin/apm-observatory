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

/**
 * 임계값·비즈니스 사이클 설정의 저장·삭제를 담당한다. 외부 데이터 판단 없이 단순 저장/조회라
 * 별도 Port 없이 Adapter만 둔다.
 */
@Component
@RequiredArgsConstructor
public class ConfigAdapter {

    private final ThresholdConfigRepository repository;
    private final BusinessCycleRepository businessCycleRepository;

    /** 있으면 수정, 없으면 신규 생성한다. 신규 생성 시 null 필드는 기본값으로 채운다. */
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

    /** 있으면 수정, 없으면 신규 생성한다(upsert). 수정 시 null 필드는 기존값을 유지한다. */
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

    /**
     * 비즈니스 사이클을 삭제한다. 삭제하면 aipipeline이 baseline fallback으로 돌아간다.
     * null로 upsert하지 않고 명시적으로 삭제해 의도를 분명히 한다.
     */
    public void deleteBusinessCycle(String appName) {
        businessCycleRepository.deleteByAppName(appName);
    }

}