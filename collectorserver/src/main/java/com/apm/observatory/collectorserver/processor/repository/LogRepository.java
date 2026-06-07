package com.apm.observatory.collectorserver.processor.repository;

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

    /**
     * 복합 PK(timestamp, app_name, thread_name)를 묶는 키 타입. 구분자로 이어 붙인
     * 문자열 키 대신 record를 쓴다. thread_name 등에 어떤 특수문자가 들어올지 보장할 수
     * 없어 구분자가 값과 충돌할 수 있다. 충돌 방지를 위해 record로 equals/hashCode를
     * 필드 기반으로 자동 생성했다.
     */
    private record LogsPk(String timestamp, String appName, String threadName) {}

    /**
     * 로그 배치를 멱등하게 저장한다. 현재 배치의 복합 PK 중 이미 저장된 것을 조회해
     * 빼고, 신규 건만 INSERT 한다. PEL 재처리로 같은 메시지가 다시 와도 중복 INSERT
     * 없이 스킵된다.
     *
     * @param batchParams 저장할 logs 행 목록. 각 Object[]의 [0]=timestamp, [1]=app_name,
     *                    [3]=thread_name이 PK다
     */
    public void saveAll(List<Object[]> batchParams) {
        if (batchParams.isEmpty()) return;

        Set<LogsPk> currentPks = batchParams.stream()
                .map(p -> new LogsPk((String) p[0], (String) p[1], (String) p[3]))
                .collect(Collectors.toSet());

        // 복합 PK 인덱스로 기존 건 조회
        Set<LogsPk> existingPks = findExistingPks(currentPks);

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

        // rs.getTimestamp → Instant → ISO-8601 문자열. batchParams의 Instant.toString()
        // 형식과 맞춰야 LogsPk Set 비교가 일치한다. JdbcTemplate은 이 타입 변환을 직접
        // 처리한다(JPA는 @Entity Instant 필드로, MyBatis는 InstantTypeHandler로 자동 처리).
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
