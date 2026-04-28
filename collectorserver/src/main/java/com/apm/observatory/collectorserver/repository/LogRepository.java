package com.apm.observatory.collectorserver.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class LogRepository {

    private final JdbcTemplate jdbcTemplate;

    public LogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 복합 PK — record로 표현
    // 구분자 방식 대신 record 선택 이유:
    //   thread_name 등 필드에 어떤 특수문자가 포함될지 보장 불가
    //   record는 equals()/hashCode()를 필드 기반으로 자동 생성 → 구분자 충돌 없음
    private record LogsPk(String timestamp, String appName, String threadName) {}

    // batch insert — SELECT로 중복 제거 후 신규 건만 INSERT
    // 멱등성(Idempotency) 보장 — PEL 재처리 시 중복 건 안전하게 스킵
    // JdbcTemplate 직접 사용 — JPA/MyBatis와 달리 타입 변환을 직접 처리
    // rs.getTimestamp().toInstant().toString() — JDBC Timestamp → Instant → ISO-8601
    // JPA라면 @Entity Instant 필드로 자동 처리, MyBatis라면 InstantTypeHandler로 처리
    public void saveAll(List<Object[]> batchParams) {
        if (batchParams.isEmpty()) return;

        // 현재 배치의 PK Set 구성
        Set<LogsPk> currentPks = batchParams.stream()
                .map(p -> new LogsPk((String) p[0], (String) p[1], (String) p[3]))
                .collect(Collectors.toSet());

        // 이미 존재하는 PK 조회 — PK 인덱스 스캔
        Set<LogsPk> existingPks = findExistingPks(currentPks);

        // 차집합 — 신규 건만 추출
        List<Object[]> newParams = batchParams.stream()
                .filter(p -> !existingPks.contains(
                        new LogsPk((String) p[0], (String) p[1], (String) p[3])))
                .toList();

        if (newParams.isEmpty()) return;

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO logs (timestamp, app_name, host, thread_name,
                    level, message, trace_id, stack_trace, error)
                VALUES (?::timestamptz, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                newParams
        );
    }

    private Set<LogsPk> findExistingPks(Set<LogsPk> pks) {
        if (pks.isEmpty()) return new HashSet<>();

        String inClause = pks.stream()
                .map(p -> "('" + p.timestamp() + "'::timestamptz, '"
                        + p.appName() + "', '" + p.threadName() + "')")
                .collect(Collectors.joining(", "));

        // rs.getTimestamp(1).toInstant().toString() — DB timestamp → JDBC Timestamp → Instant → ISO-8601
        // batchParams의 Instant.toString() 형식과 일치시키기 위함
        return jdbcTemplate.query(
                "SELECT timestamp, app_name, thread_name FROM logs " +
                        "WHERE (timestamp, app_name, thread_name) IN (" + inClause + ")",
                (rs, rowNum) -> new LogsPk(
                        rs.getTimestamp(1).toInstant().toString(),
                        rs.getString("app_name"),
                        rs.getString("thread_name"))
        ).stream().collect(Collectors.toSet());
    }

}