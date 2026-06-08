package com.apm.observatory.aipipeline.threshold.port;

import com.apm.observatory.aipipeline.threshold.model.ThresholdConfig;

import java.util.List;
import java.util.Optional;

/** threshold_config 조회 계약. */
public interface ThresholdConfigPort {

    List<ThresholdConfig> findAll();

    Optional<ThresholdConfig> findByAppName(String appName);

}