package com.apm.observatory.collectorserver.processor;

import com.apm.observatory.collectorserver.repository.LogRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LogProcessor {

    private final LogRepository logRepository;

    public LogProcessor(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    // 로그는 Metrics/Spans와 달리 가공 없이 수신 데이터 그대로 저장
    // Metrics: Disk IO 누적값 계산 필요
    // Spans: TraceID 기준 수집 대기 + INTERNAL 파생 계산 필요
    // Logs: 타임스탬프 변환 외 별도 가공 없음
    public void process(List<Map<String, String>> messages) {
        if (messages.isEmpty()) return;

        List<Object[]> batchParams = new ArrayList<>();

        for (Map<String, String> m : messages) {
            batchParams.add(new Object[]{
                    toIso(m.get("timestamp")),
                    m.get("app_name"),
                    m.get("host"),
                    m.get("thread_name"),
                    m.get("level"),
                    m.get("message"),
                    m.get("trace_id"),
                    m.get("stack_trace"),
                    Boolean.parseBoolean(m.get("error"))
            });
        }

        logRepository.saveAll(batchParams);
    }

    // epoch milliseconds → ISO-8601 문자열
    // PostgreSQL ?::timestamptz 가 문자열을 받으므로 변환 필요
    private String toIso(String epochMs) {
        if (epochMs == null || epochMs.isBlank()) return null;
        return Instant.ofEpochMilli(Long.parseLong(epochMs)).toString();
    }

}