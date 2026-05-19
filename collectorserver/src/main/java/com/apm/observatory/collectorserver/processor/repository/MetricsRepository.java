package com.apm.observatory.collectorserver.processor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class MetricsRepository {

    private final JdbcTemplate jdbcTemplate;

    public MetricsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 복합 PK — record로 표현
    // 구분자 방식 대신 record 선택 이유:
    //   app_name 등 필드에 어떤 특수문자가 포함될지 보장 불가
    //   record는 equals()/hashCode()를 필드 기반으로 자동 생성 → 구분자 충돌 없음
    private record MetricsPk(String timestamp, String appName) {}

    // batch insert — SELECT로 중복 제거 후 신규 건만 INSERT
    // 멱등성(Idempotency) 보장 — PEL 재처리 시 중복 건 안전하게 스킵
    public void saveAll(List<Object[]> batchParams) {
        if (batchParams.isEmpty()) return;

        // 현재 배치의 PK Set 구성
        Set<MetricsPk> currentPks = batchParams.stream()
                .map(p -> new MetricsPk((String) p[0], (String) p[1]))
                .collect(Collectors.toSet());

        // 이미 존재하는 PK 조회 — PK 인덱스 스캔
        Set<MetricsPk> existingPks = findExistingPks(currentPks);

        // 차집합 — 신규 건만 추출
        List<Object[]> newParams = batchParams.stream()
                .filter(p -> !existingPks.contains(new MetricsPk((String) p[0], (String) p[1])))
                .toList();

        if (newParams.isEmpty()) return;

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO metrics (timestamp, app_name, host, ip,
                    cpu_usage, heap_used, heap_max, thread_count,
                    disk_used, disk_total, disk_read_bytes, disk_write_bytes)
                VALUES (?::timestamptz, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                newParams
        );
    }

    private Set<MetricsPk> findExistingPks(Set<MetricsPk> pks) {
        if (pks.isEmpty()) return new HashSet<>();

        String inClause = pks.stream()
                .map(p -> "('" + p.timestamp() + "'::timestamptz, '" + p.appName() + "')")
                .collect(Collectors.joining(", "));

        // rs.getTimestamp(1).toInstant().toString() — DB timestamp → JDBC Timestamp → Instant → ISO-8601
        // batchParams의 Instant.toString() 형식과 일치시키기 위함
        return jdbcTemplate.query(
                "SELECT timestamp, app_name FROM metrics " +
                        "WHERE (timestamp, app_name) IN (" + inClause + ")",
                (rs, rowNum) -> new MetricsPk(
                        rs.getTimestamp(1).toInstant().toString(),
                        rs.getString("app_name"))
        ).stream().collect(Collectors.toSet());
    }

}