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

    /**
     * 복합 PK(timestamp, app_name)를 묶는 키 타입. 구분자로 이어 붙인 문자열 키 대신
     * record를 쓴다. app_name 등에 어떤 특수문자가 들어올지 보장할 수 없어 구분자가
     * 값과 충돌할 수 있다. 충돌 방지를 위해 record로 equals/hashCode를 필드 기반으로
     * 자동 생성했다.
     */
    private record MetricsPk(String timestamp, String appName) {}

    /**
     * 메트릭 배치를 멱등하게 저장한다. 현재 배치의 복합 PK 중 이미 저장된 것을 조회해
     * 빼고, 신규 건만 INSERT 한다. PEL 재처리로 같은 메시지가 다시 와도 중복 INSERT
     * 없이 스킵된다.
     *
     * @param batchParams 저장할 metrics 행 목록. 각 Object[]의 [0]=timestamp, [1]=app_name이 PK다
     */
    public void saveAll(List<Object[]> batchParams) {
        if (batchParams.isEmpty()) return;

        Set<MetricsPk> currentPks = batchParams.stream()
                .map(p -> new MetricsPk((String) p[0], (String) p[1]))
                .collect(Collectors.toSet());

        // 복합 PK 인덱스로 기존 건 조회
        Set<MetricsPk> existingPks = findExistingPks(currentPks);

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

        // rs.getTimestamp → Instant → ISO-8601 문자열. batchParams의 Instant.toString()
        // 형식과 맞춰야 MetricsPk Set 비교가 일치한다.
        return jdbcTemplate.query(
                "SELECT timestamp, app_name FROM metrics " +
                        "WHERE (timestamp, app_name) IN (" + inClause + ")",
                (rs, rowNum) -> new MetricsPk(
                        rs.getTimestamp(1).toInstant().toString(),
                        rs.getString("app_name"))
        ).stream().collect(Collectors.toSet());
    }

}
