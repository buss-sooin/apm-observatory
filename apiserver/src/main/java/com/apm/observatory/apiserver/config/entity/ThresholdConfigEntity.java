package com.apm.observatory.apiserver.config.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "threshold_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThresholdConfigEntity {

    @Id
    private String id;

    @Column(name = "app_name", nullable = false, unique = true)
    private String appName;

    @Column(name = "cpu_threshold")
    private Double cpuThreshold;

    @Column(name = "memory_threshold")
    private Double memoryThreshold;

    @Column(name = "disk_io_threshold")
    private Long diskIoThreshold;

    @Column(name = "span_duration_multiplier")
    private Double spanDurationMultiplier;

    @Column(name = "external_ratio_multiplier")
    private Double externalRatioMultiplier;

    @Column(name = "slope_min_positive")
    private Double slopeMinPositive;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // 의도: 신규 생성용 생성자
    public ThresholdConfigEntity(String id, String appName,
                                 double cpuThreshold, double memoryThreshold,
                                 long diskIoThreshold, double spanDurationMultiplier,
                                 double externalRatioMultiplier, double slopeMinPositive) {
        this.id = id;
        this.appName = appName;
        this.cpuThreshold = cpuThreshold;
        this.memoryThreshold = memoryThreshold;
        this.diskIoThreshold = diskIoThreshold;
        this.spanDurationMultiplier = spanDurationMultiplier;
        this.externalRatioMultiplier = externalRatioMultiplier;
        this.slopeMinPositive = slopeMinPositive;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // 의도: 수정용 생성자 - 기존 id, appName, createdAt 유지
    // null이면 기존값 유지 (방법 A: Adapter에서 명시적 null 방어)
    public ThresholdConfigEntity updateWith(Double cpuThreshold, Double memoryThreshold,
                                            Long diskIoThreshold, Double spanDurationMultiplier,
                                            Double externalRatioMultiplier, Double slopeMinPositive) {
        ThresholdConfigEntity updated = new ThresholdConfigEntity();
        updated.id = this.id;
        updated.appName = this.appName;
        updated.createdAt = this.createdAt;
        updated.updatedAt = Instant.now();
        updated.cpuThreshold = cpuThreshold != null ? cpuThreshold : this.cpuThreshold;
        updated.memoryThreshold = memoryThreshold != null ? memoryThreshold : this.memoryThreshold;
        updated.diskIoThreshold = diskIoThreshold != null ? diskIoThreshold : this.diskIoThreshold;
        updated.spanDurationMultiplier = spanDurationMultiplier != null ? spanDurationMultiplier : this.spanDurationMultiplier;
        updated.externalRatioMultiplier = externalRatioMultiplier != null ? externalRatioMultiplier : this.externalRatioMultiplier;
        updated.slopeMinPositive = slopeMinPositive != null ? slopeMinPositive : this.slopeMinPositive;
        return updated;
    }

}