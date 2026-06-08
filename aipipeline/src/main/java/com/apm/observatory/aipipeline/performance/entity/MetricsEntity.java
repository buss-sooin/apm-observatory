package com.apm.observatory.aipipeline.performance.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.Instant;

/** metrics 테이블 매핑(읽기 전용). (timestamp, app_name) 복합 PK. */
@Entity
@Table(name = "metrics")
@IdClass(MetricsEntity.MetricsPK.class)
@Getter
@NoArgsConstructor
public class MetricsEntity {

    @Id
    @Column(name = "timestamp")
    private Instant timestamp;

    @Id
    @Column(name = "app_name")
    private String appName;

    @Column(name = "host")
    private String host;

    @Column(name = "cpu_usage")
    private Double cpuUsage;

    @Column(name = "heap_used")
    private Long heapUsed;

    @Column(name = "heap_max")
    private Long heapMax;

    @Column(name = "thread_count")
    private Integer threadCount;

    @Column(name = "disk_read_bytes")
    private Long diskReadBytes;

    @Column(name = "disk_write_bytes")
    private Long diskWriteBytes;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class MetricsPK implements Serializable {
        private Instant timestamp;
        private String appName;
    }

}