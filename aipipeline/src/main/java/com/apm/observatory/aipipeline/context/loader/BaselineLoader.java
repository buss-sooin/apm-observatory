package com.apm.observatory.aipipeline.context.loader;

import com.apm.observatory.aipipeline.context.model.BaselineMetrics;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort;

import java.time.Instant;

/**
 * baseline 지표 적재 방식을 주입하는 함수형 인터페이스.
 * Port 두 개와 기간을 받아 {@link BaselineMetrics}를 만든다.
 */
@FunctionalInterface
public interface BaselineLoader {
    BaselineMetrics load(PerformanceDataPort port,
                         ExternalImpactDataPort extPort,
                         Instant start,
                         Instant end);
}