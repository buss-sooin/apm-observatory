package com.apm.observatory.aipipeline.threshold.businesscycle.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "business_cycle")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BusinessCycleEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "app_name", nullable = false, unique = true)
    private String appName;

    @Column(name = "cycle_start", nullable = false)
    private LocalTime cycleStart;

    @Column(name = "cycle_end", nullable = false)
    private LocalTime cycleEnd;

    @Column(name = "peak_start", nullable = false)
    private LocalTime peakStart;

    @Column(name = "peak_end", nullable = false)
    private LocalTime peakEnd;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

}