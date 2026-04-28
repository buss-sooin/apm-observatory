package com.apm.observatory.aipipeline.threshold.businesscycle.port;

import com.apm.observatory.aipipeline.threshold.businesscycle.model.BusinessCycle;

import java.util.Optional;

public interface BusinessCyclePort {
    Optional<BusinessCycle> findByAppName(String appName);
}