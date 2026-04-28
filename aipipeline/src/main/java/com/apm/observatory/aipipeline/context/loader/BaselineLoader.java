package com.apm.observatory.aipipeline.context.loader;

import com.apm.observatory.aipipeline.context.model.BaselineMetrics;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort;

import java.time.Instant;

@FunctionalInterface
public interface BaselineLoader {
    BaselineMetrics load(PerformanceDataPort port,
                         ExternalImpactDataPort extPort,
                         Instant start,
                         Instant end);
}