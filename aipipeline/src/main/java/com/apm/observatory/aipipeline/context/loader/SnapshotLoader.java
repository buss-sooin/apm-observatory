package com.apm.observatory.aipipeline.context.loader;

import com.apm.observatory.aipipeline.context.model.BaselineMetrics;
import com.apm.observatory.aipipeline.performance.model.PerformanceSnapshot;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort;

import java.time.Instant;

/**
 * 최근 구간 스냅샷 적재 방식을 주입하는 함수형 인터페이스.
 * Port 두 개·기간·baseline을 받아 {@link PerformanceSnapshot}을 만든다.
 */
@FunctionalInterface
public interface SnapshotLoader {
    PerformanceSnapshot load(PerformanceDataPort port,
                             ExternalImpactDataPort extPort,
                             Instant start,
                             Instant end,
                             BaselineMetrics baseline);
}