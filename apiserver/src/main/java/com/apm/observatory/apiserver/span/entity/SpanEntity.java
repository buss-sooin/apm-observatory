package com.apm.observatory.apiserver.span.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.Instant;

/**
 * spans 테이블 읽기 전용 매핑. collectorserver가 저장하고 apiserver는 조회만 한다.
 */
@Entity
@Table(name = "spans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
    private boolean error;

    @Column(name = "error_message")
    private String errorMessage;

}