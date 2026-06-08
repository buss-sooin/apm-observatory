package com.apm.observatory.aipipeline.threshold.adapter;

import com.apm.observatory.aipipeline.config.AiPipelineConfig;
import com.apm.observatory.aipipeline.threshold.model.ThresholdConfig;
import com.apm.observatory.aipipeline.threshold.port.ThresholdConfigPort;
import com.apm.observatory.aipipeline.threshold.repository.ThresholdConfigRepository;
import com.apm.observatory.aipipeline.threshold.entity.ThresholdConfigEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * threshold_config 조회 어댑터. Entity를 도메인 {@link ThresholdConfig}로 옮기며,
 * 앱별로 지정되지 않은(null) 임계값은 {@link AiPipelineConfig}의 전역 기본값으로
 * 채운다.
 */
@Component
@RequiredArgsConstructor
public class ThresholdConfigAdapter implements ThresholdConfigPort {

    private final ThresholdConfigRepository thresholdConfigRepository;
    private final AiPipelineConfig config;

    @Override
    public List<ThresholdConfig> findAll() {
        return thresholdConfigRepository.findAll()
                .stream()
                .map(this::toModel)
                .toList();
    }

    @Override
    public Optional<ThresholdConfig> findByAppName(String appName) {
        return thresholdConfigRepository.findByAppName(appName)
                .map(this::toModel);
    }

    private ThresholdConfig toModel(ThresholdConfigEntity entity) {
        return new ThresholdConfig(
                entity.getAppName(),
                entity.getCpuThreshold() != null
                        ? entity.getCpuThreshold()
                        : config.threshold().cpuThreshold(),
                entity.getMemoryThreshold() != null
                        ? entity.getMemoryThreshold()
                        : config.threshold().memoryThreshold(),
                entity.getSpanDurationMultiplier() != null
                        ? entity.getSpanDurationMultiplier()
                        : config.threshold().spikeMultiplier(),
                entity.getExternalRatioMultiplier() != null
                        ? entity.getExternalRatioMultiplier()
                        : config.threshold().externalRatioMultiplier(),
                entity.getSlopeMinPositive() != null
                        ? entity.getSlopeMinPositive()
                        : config.threshold().slopeMinPositive()
        );
    }

}