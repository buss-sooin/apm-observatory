package com.apm.observatory.aipipeline.performance.model;

import com.apm.observatory.aipipeline.analysis.model.ErosionDataPoint;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * erosion 판정을 위해 한 앱의 추세 포인트를 누적하는 상태 객체.
 * contextStartTime부터 erosion 윈도우가 지나면 만료로 보고, 만료 시점의
 * 누적 포인트로 기울기를 평가한다.
 */
public class PerformanceTrend {

    private final String appName;
    private final Instant contextStartTime;
    private final List<ErosionDataPoint> points = new ArrayList<>();

    public PerformanceTrend(String appName, Instant contextStartTime) {
        this.appName = appName;
        this.contextStartTime = contextStartTime;
    }

    public void addPoint(ErosionDataPoint point) {
        points.add(point);
    }

    public List<ErosionDataPoint> getPoints() {
        return List.copyOf(points);
    }

    public String getAppName() {
        return appName;
    }

    public Instant getContextStartTime() {
        return contextStartTime;
    }

    public boolean isExpired(Instant now, long erosionWindowMinutes) {
        Instant expiredAt = contextStartTime.plus(erosionWindowMinutes, ChronoUnit.MINUTES);
        return now.isAfter(expiredAt) || now.equals(expiredAt);
    }

}