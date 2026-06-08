package com.apm.observatory.aipipeline.threshold.businesscycle.model;

import java.time.LocalTime;

/**
 * 앱별 영업 주기·피크 구간 설정. threshold와 같은 앱별 운영 설정이라
 * threshold 패키지 하위에 두며, baseline 비교 구간을 정할 때 쓰인다.
 */
public record BusinessCycle(
        String appName,
        LocalTime cycleStart,
        LocalTime cycleEnd,
        LocalTime peakStart,
        LocalTime peakEnd
) {}