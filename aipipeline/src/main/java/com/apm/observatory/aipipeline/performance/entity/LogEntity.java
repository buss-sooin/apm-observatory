package com.apm.observatory.aipipeline.performance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

/** logs 테이블 매핑(읽기 전용). (timestamp, app_name, thread_name) 복합 PK. */
@Entity
@Table(name = "logs")
@IdClass(LogEntity.LogPK.class)
@Getter
@NoArgsConstructor
public class LogEntity {

    @Id
    @Column(name = "timestamp")
    private Instant timestamp;

    @Id
    @Column(name = "app_name")
    private String appName;

    @Id
    @Column(name = "thread_name")
    private String threadName;

    @Column(name = "host")
    private String host;

    @Column(name = "level")
    private String level;

    @Column(name = "message")
    private String message;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "stack_trace")
    private String stackTrace;

    @Column(name = "error")
    private Boolean error;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class LogPK implements Serializable {
        private Instant timestamp;
        private String appName;
        private String threadName;
    }

}