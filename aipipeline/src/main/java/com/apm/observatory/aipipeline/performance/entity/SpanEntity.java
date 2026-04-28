package com.apm.observatory.aipipeline.performance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "spans")
@Getter
@NoArgsConstructor
public class SpanEntity {

    @Id
    @Column(name = "span_id")
    private String spanId;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "parent_span_id")
    private String parentSpanId;

    @Column(name = "app_name")
    private String appName;

    @Column(name = "host")
    private String host;

    @Column(name = "span_type")
    private String spanType;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "http_method")
    private String httpMethod;

    @Column(name = "http_url")
    private String httpUrl;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "sql_query")
    private String sqlQuery;

    @Column(name = "external_host")
    private String externalHost;

    @Column(name = "error")
    private Boolean error;

    @Column(name = "error_message")
    private String errorMessage;

}