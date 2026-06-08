package com.apm.observatory.apiserver.log.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * logs 테이블 읽기 전용 매핑. collectorserver가 저장하고 apiserver는 조회만 한다.
 */
@Entity
@Table(name = "logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LogEntity {

    @EmbeddedId
    private LogPk id;

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
    private boolean error;

    /** logs 테이블의 복합 PK(timestamp + app_name + thread_name). */
    @Embeddable
    @Getter
    public static class LogPk implements Serializable {

        @Column(name = "timestamp")
        private Instant timestamp;

        @Column(name = "app_name")
        private String appName;

        @Column(name = "thread_name")
        private String threadName;

        protected LogPk() {}

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LogPk that)) return false;
            return Objects.equals(timestamp, that.timestamp) &&
                    Objects.equals(appName, that.appName) &&
                    Objects.equals(threadName, that.threadName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(timestamp, appName, threadName);
        }
    }

}