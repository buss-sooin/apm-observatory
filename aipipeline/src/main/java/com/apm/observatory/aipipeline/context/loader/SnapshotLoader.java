package com.apm.observatory.aipipeline.context.loader;

import com.apm.observatory.aipipeline.context.model.BaselineMetrics;
import com.apm.observatory.aipipeline.performance.model.PerformanceSnapshot;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort;

import java.time.Instant;

@FunctionalInterface
public interface SnapshotLoader {
    PerformanceSnapshot load(PerformanceDataPort port,
                             ExternalImpactDataPort extPort,
                             Instant start,
                             Instant end,
                             BaselineMetrics baseline);
}