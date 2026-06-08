package com.apm.observatory.apiserver.log.model;

import java.time.Instant;

public class LogModel {

    /** GET /logs/stream 응답 원소. */
    public record LogEntry(
            Instant timestamp,
            String appName,
            String threadName,
            String level,
            String message,
            String traceId,
            String stackTrace,
            boolean error
    ) {}

}