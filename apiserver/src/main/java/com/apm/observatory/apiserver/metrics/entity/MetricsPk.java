package com.apm.observatory.apiserver.metrics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

// 의도: metrics 테이블의 복합 PK (timestamp + app_name)
// TimescaleDB hypertable 기준 - collectorserver와 동일한 PK 구조
@Embeddable
public class MetricsPk implements Serializable {

    @Column(name = "timestamp")
    private Instant timestamp;

    @Column(name = "app_name")
    private String appName;

    protected MetricsPk() {}

    public MetricsPk(Instant timestamp, String appName) {
        this.timestamp = timestamp;
        this.appName = appName;
    }

    public Instant getTimestamp() { return timestamp; }
    public String getAppName() { return appName; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetricsPk that)) return false;
        return Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(appName, that.appName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, appName);
    }

}