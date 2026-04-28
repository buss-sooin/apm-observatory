package com.apm.observatory.aipipeline.threshold.port;

import com.apm.observatory.aipipeline.threshold.model.ThresholdConfig;

import java.util.List;
import java.util.Optional;

public interface ThresholdConfigPort {

    List<ThresholdConfig> findAll();

    Optional<ThresholdConfig> findByAppName(String appName);

}