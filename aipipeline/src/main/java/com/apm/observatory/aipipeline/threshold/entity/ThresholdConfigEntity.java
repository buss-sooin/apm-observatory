package com.apm.observatory.aipipeline.threshold.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** threshold_config 테이블 매핑(읽기 전용). 앱별 임계값을 보관한다. */
@Entity
@Table(name = "threshold_config")
@Getter
@NoArgsConstructor
public class ThresholdConfigEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "app_name", unique = true, nullable = false)
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

}