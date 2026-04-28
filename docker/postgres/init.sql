-- TimescaleDB 익스텐션 활성화
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- metrics 테이블
CREATE TABLE IF NOT EXISTS metrics (
                                       timestamp       TIMESTAMPTZ     NOT NULL,
                                       app_name        VARCHAR(100)    NOT NULL,
    host            VARCHAR(100)    NOT NULL,
    ip              VARCHAR(50)     NOT NULL,
    cpu_usage       DOUBLE PRECISION,
    heap_used       BIGINT,
    heap_max        BIGINT,
    thread_count    INTEGER,
    disk_used       BIGINT,
    disk_total      BIGINT,
    disk_read_bytes BIGINT,
    disk_write_bytes BIGINT,
    PRIMARY KEY (timestamp, app_name)
    );

-- metrics hypertable 변환
SELECT create_hypertable('metrics', 'timestamp');

-- spans 테이블
CREATE TABLE IF NOT EXISTS spans (
    span_id         VARCHAR(50)     PRIMARY KEY,
    trace_id        VARCHAR(50)     NOT NULL,
    parent_span_id  VARCHAR(50),
    app_name        VARCHAR(100)    NOT NULL,
    host            VARCHAR(100)    NOT NULL,
    span_type       VARCHAR(20)     NOT NULL,
    start_time      TIMESTAMPTZ     NOT NULL,
    end_time        TIMESTAMPTZ,
    duration_ms     BIGINT,
    http_method     VARCHAR(10),
    http_url        VARCHAR(500),
    http_status     INTEGER,
    sql_query       TEXT,
    external_host   VARCHAR(200),
    error           BOOLEAN         DEFAULT FALSE,
    error_message   TEXT
    );

CREATE INDEX idx_spans_trace_id ON spans(trace_id);
CREATE INDEX idx_spans_start_time ON spans(start_time);

-- logs 테이블
CREATE TABLE IF NOT EXISTS logs (
                                    timestamp       TIMESTAMPTZ     NOT NULL,
                                    app_name        VARCHAR(100)    NOT NULL,
    host            VARCHAR(100)    NOT NULL,
    thread_name     VARCHAR(200)    NOT NULL,
    level           VARCHAR(10)     NOT NULL,
    message         TEXT,
    trace_id        VARCHAR(50),
    stack_trace     TEXT,
    error           BOOLEAN         DEFAULT FALSE,
    PRIMARY KEY (timestamp, app_name, thread_name)
    );

CREATE INDEX idx_logs_trace_id ON logs(trace_id);
CREATE INDEX idx_logs_level ON logs(level);

-- ai_analysis_results 테이블
CREATE TABLE IF NOT EXISTS ai_analysis_results (
    id                  VARCHAR(50)     PRIMARY KEY,
    timestamp           TIMESTAMPTZ     NOT NULL,
    trace_id            VARCHAR(50),
    app_name            VARCHAR(100)    NOT NULL,
    fusion_criteria     INTEGER         NOT NULL,
    pattern_type        INTEGER         NOT NULL,
    span_type           VARCHAR(20),
    severity            VARCHAR(10)     NOT NULL,
    ai_summary          TEXT,
    root_cause          TEXT,
    recommendation      TEXT,
    analysis_start_time TIMESTAMPTZ,
    analysis_end_time   TIMESTAMPTZ
    );

CREATE INDEX idx_ai_results_trace_id ON ai_analysis_results(trace_id);
CREATE INDEX idx_ai_results_timestamp ON ai_analysis_results(timestamp);
CREATE INDEX idx_ai_results_fusion_pattern ON ai_analysis_results(fusion_criteria, pattern_type);

-- ai_analysis_span_evidence 테이블 (분석 근거 Span)
CREATE TABLE IF NOT EXISTS ai_analysis_span_evidence (
    id              VARCHAR(50)     PRIMARY KEY,
    analysis_id     VARCHAR(50)     NOT NULL REFERENCES ai_analysis_results(id),
    span_id         VARCHAR(50)     NOT NULL REFERENCES spans(span_id)
    );

CREATE INDEX idx_span_evidence_analysis_id ON ai_analysis_span_evidence(analysis_id);

-- ai_analysis_metrics_evidence 테이블 (분석 근거 Metrics)
CREATE TABLE IF NOT EXISTS ai_analysis_metrics_evidence (
    id              VARCHAR(50)     PRIMARY KEY,
    analysis_id     VARCHAR(50)     NOT NULL REFERENCES ai_analysis_results(id),
    metric_timestamp TIMESTAMPTZ    NOT NULL,
    metric_app_name  VARCHAR(100)   NOT NULL,
    FOREIGN KEY (metric_timestamp, metric_app_name) REFERENCES metrics(timestamp, app_name)
    );

CREATE INDEX idx_metrics_evidence_analysis_id ON ai_analysis_metrics_evidence(analysis_id);

-- business_cycle 테이블
CREATE TABLE IF NOT EXISTS business_cycle (
    id                  VARCHAR(50)     PRIMARY KEY,
    app_name            VARCHAR(100)    NOT NULL,
    cycle_start         TIME            NOT NULL,
    cycle_end           TIME            NOT NULL,
    peak_start          TIME            NOT NULL,
    peak_end            TIME            NOT NULL,
    created_at          TIMESTAMPTZ     DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     DEFAULT NOW()
    );

-- threshold_config 테이블
CREATE TABLE IF NOT EXISTS threshold_config (
    id                          VARCHAR(50)     PRIMARY KEY,
    app_name                    VARCHAR(100)    NOT NULL UNIQUE,
    cpu_threshold               DOUBLE PRECISION DEFAULT 80.0,
    memory_threshold            DOUBLE PRECISION DEFAULT 80.0,
    disk_io_threshold           BIGINT          DEFAULT 100000000,
    span_duration_multiplier    DOUBLE PRECISION DEFAULT 3.0,
    external_ratio_multiplier   DOUBLE PRECISION DEFAULT 3.0,
    slope_min_positive DOUBLE PRECISION DEFAULT 0.01,
    created_at                  TIMESTAMPTZ     DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     DEFAULT NOW()
    );

-- users 테이블
CREATE TABLE IF NOT EXISTS users (
    id          VARCHAR(50)     PRIMARY KEY,
    username    VARCHAR(100)    NOT NULL UNIQUE,
    password    VARCHAR(255)    NOT NULL,
    role        VARCHAR(20)     NOT NULL,
    created_at  TIMESTAMPTZ     DEFAULT NOW()
    );

-- admin 초기 계정
INSERT INTO users (id, username, password, role)
VALUES ('1', 'admin', '$2a$10$iax55Nia5Zw28uqgzrW1AuxdXSWWkV7QWnaU7pjK0F.vqCn.gZW6a', 'ROLE_ADMIN');

-- erosion_trend_slopes 테이블
-- aipipeline이 Erosion 판단 시마다 slope 저장 (DETECTED/NOT_DETECTED 무관)
-- DETECTED 시: analysis_id 채워짐 (ai_analysis_results와 연결)
-- NOT_DETECTED 시: analysis_id = null (slope만 독립 저장)
-- apiserver는 이 테이블을 조회만 함 (계산 책임은 aipipeline)
CREATE TABLE IF NOT EXISTS erosion_trend_slopes (
    id                  VARCHAR(50)         PRIMARY KEY,
    analysis_id         VARCHAR(50)         REFERENCES ai_analysis_results(id),
    app_name            VARCHAR(100)        NOT NULL,
    timestamp           TIMESTAMPTZ         NOT NULL,
    resource_slope      DOUBLE PRECISION    NOT NULL,
    response_slope      DOUBLE PRECISION    NOT NULL
    );

-- app_name + timestamp DESC: apiserver에서 앱별 최근 slope 추세 조회 시 인덱스 활용
CREATE INDEX idx_erosion_slopes_app_name_timestamp
    ON erosion_trend_slopes(app_name, timestamp DESC);

-- ai_raw_responses 테이블
-- Ollama 호출 시 항상 저장 — 파싱 성공/실패 무관
-- parse_status: SUCCESS / JSON_PARSE_FAILED / VALIDATION_FAILED
-- analysis_id: SUCCESS 시 ai_analysis_results.id 연결 (nullable)
CREATE TABLE IF NOT EXISTS ai_raw_responses (
    id              VARCHAR(50)     PRIMARY KEY,
    app_name        VARCHAR(100)    NOT NULL,
    fusion_criteria INTEGER         NOT NULL,
    raw_response    TEXT,
    parse_status    VARCHAR(30)     NOT NULL,
    error_message   TEXT,
    analysis_id     VARCHAR(50)     REFERENCES ai_analysis_results(id),
    timestamp       TIMESTAMPTZ     NOT NULL
    );

CREATE INDEX idx_raw_responses_app_name ON ai_raw_responses(app_name);
CREATE INDEX idx_raw_responses_parse_status ON ai_raw_responses(parse_status);
CREATE INDEX idx_raw_responses_analysis_id ON ai_raw_responses(analysis_id);

-- targetappmvc 기본 임계값 설정 (데모용)
INSERT INTO threshold_config (id, app_name)
VALUES ('1', 'targetappmvc');