package com.apm.observatory.apiserver.config.entity;

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

    /**
     * 수정본을 만든다. id·appName·createdAt은 유지하고 updatedAt만 새로 찍는다.
     * 전달값이 null인 필드는 기존값을 그대로 유지한다.
     */
    public BusinessCycleEntity updateWith(
            LocalTime cycleStart, LocalTime cycleEnd,
            LocalTime peakStart, LocalTime peakEnd) {
        return BusinessCycleEntity.builder()
                .id(this.id)
                .appName(this.appName)
                .cycleStart(cycleStart != null ? cycleStart : this.cycleStart)
                .cycleEnd(cycleEnd != null ? cycleEnd : this.cycleEnd)
                .peakStart(peakStart != null ? peakStart : this.peakStart)
                .peakEnd(peakEnd != null ? peakEnd : this.peakEnd)
                .createdAt(this.createdAt)
                .updatedAt(Instant.now())
                .build();
    }

}