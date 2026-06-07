package com.apm.observatory.collectorserver.processor;

import com.apm.observatory.collectorserver.processor.repository.LogRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis Stream에서 받은 로그 메시지를 저장 형태로 바꿔 batch 저장하는 컴포넌트.
 *
 * <p>세 processor 중 가장 단순하다. 로그는 timestamp를 ISO-8601로 바꾸는 것 외에
 * 도메인 가공이 없어, 받은 컬럼을 그대로 매핑해 {@link LogRepository}로 넘긴다.
 * span처럼 buffer에 모았다가 트리를 조립하거나 파생 값을 계산하는 단계가 없다.
 */
@Component
public class LogProcessor {

    private final LogRepository logRepository;

    public LogProcessor(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 로그 메시지 묶음을 변환해 batch 저장한다.
     *
     * @param messages 한 폴링에서 받은 로그 메시지 묶음(컬럼-값 매핑)
     */
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

    // epoch milliseconds → ISO-8601 문자열 (PostgreSQL ?::timestamptz 입력용)
    private String toIso(String epochMs) {
        if (epochMs == null || epochMs.isBlank()) return null;
        return Instant.ofEpochMilli(Long.parseLong(epochMs)).toString();
    }

}
