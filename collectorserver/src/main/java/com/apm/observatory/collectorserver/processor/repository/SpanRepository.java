package com.apm.observatory.collectorserver.processor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class SpanRepository {

    private final JdbcTemplate jdbcTemplate;

    public SpanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // spans PK는 span_id 단일 컬럼 — record 불필요, String으로 충분
    // batch insert — SELECT로 중복 제거 후 신규 건만 INSERT
    // 멱등성(Idempotency) 보장 — PEL 재처리 시 중복 건 안전하게 스킵
    public void saveAll(List<Object[]> batchParams) {
        if (batchParams.isEmpty()) return;

        // 현재 배치의 span_id Set 구성
        Set<String> currentIds = batchParams.stream()
                .map(p -> (String) p[0])
                .collect(Collectors.toSet());

        // 이미 존재하는 span_id 조회
        Set<String> existingIds = findExistingIds(currentIds);

        // 차집합 — 신규 건만 추출
        List<Object[]> newParams = batchParams.stream()
                .filter(p -> !existingIds.contains((String) p[0]))
                .toList();

        if (newParams.isEmpty()) return;

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO spans (span_id, trace_id, parent_span_id, app_name, host,
                    span_type, start_time, end_time, duration_ms,
                    http_method, http_url, http_status,
                    sql_query, external_host, error, error_message)
                VALUES (?, ?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?,
                        ?, ?, ?, ?, ?, ?, ?)
                """,
                newParams
        );
    }

    private Set<String> findExistingIds(Set<String> ids) {
        if (ids.isEmpty()) return new HashSet<>();

        String inClause = ids.stream()
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(", "));

        return jdbcTemplate.query(
                "SELECT span_id FROM spans WHERE span_id IN (" + inClause + ")",
                (rs, rowNum) -> rs.getString("span_id")
        ).stream().collect(Collectors.toSet());
    }

}