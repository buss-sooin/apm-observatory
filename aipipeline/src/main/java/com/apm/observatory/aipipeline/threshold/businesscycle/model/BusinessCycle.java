package com.apm.observatory.aipipeline.threshold.businesscycle.model;

import java.time.LocalTime;

// 의도: threshold 하위 운영 설정값 — business_cycle
// threshold와 동일한 성격(앱별 설정)이라 threshold 패키지 하위에 위치
// 없으면 PerformanceContextManager가 baseline fallback(최근 N분 평균) 실행
// 있으면 전날 동시간대를 baseline으로 사용
public record BusinessCycle(
        String appName,
        LocalTime cycleStart,
        LocalTime cycleEnd,
        LocalTime peakStart,
        LocalTime peakEnd
) {}