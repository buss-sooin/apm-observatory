package com.apm.observatory.aipipeline.performance.model;

import com.apm.observatory.aipipeline.analysis.model.ErosionDataPoint;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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