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

    /**
     * span 배치를 멱등하게 저장한다. 현재 배치의 span_id 중 이미 저장된 것을 조회해
     * 빼고, 신규 건만 INSERT 한다. PEL 재처리로 같은 메시지가 다시 와도 중복 INSERT
     * 없이 스킵된다.
     *
     * <p>spans의 PK는 span_id 단일 컬럼이라 String Set으로 중복을 판정한다. 복합 PK를
     * 쓰는 metrics·logs는 같은 판정을 record로 묶는다.
     *
     * @param batchParams 저장할 span 행 목록. 각 Object[]의 첫 항목이 span_id다
     */
    public void saveAll(List<Object[]> batchParams) {
        if (batchParams.isEmpty()) return;

        Set<String> currentIds = batchParams.stream()
                .map(p -> (String) p[0])
                .collect(Collectors.toSet());

        Set<String> existingIds = findExistingIds(currentIds);

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
