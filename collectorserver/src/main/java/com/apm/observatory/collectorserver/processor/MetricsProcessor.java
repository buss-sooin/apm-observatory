package com.apm.observatory.collectorserver.processor;

import com.apm.observatory.collectorserver.processor.repository.MetricsRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MetricsProcessor {

    private final MetricsRepository metricsRepository;

    // Disk IO 누적값(read/write bytes)은 Java 표준 API 미제공
    // Disk IO 누적값은 표준 API로는 못 가져와서, 정확히 하려면 OS 레벨로 내려가야 할 것 같음
    // 지금은 에이전트가 0으로 전송하고 수집 서버도 0 그대로 저장하는 방식으로 처리함
    public MetricsProcessor(MetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    public void process(List<Map<String, String>> messages) {
        if (messages.isEmpty()) return;

        List<Object[]> batchParams = new ArrayList<>();

        for (Map<String, String> m : messages) {
            String appName        = m.get("app_name");
            long currentTimestamp = parseLong(m.get("timestamp"));
            String host           = m.get("host");
            String ip             = m.get("ip");

            double cpuUsage   = parseDouble(m.get("cpu_usage"));
            long heapUsed     = parseLong(m.get("heap_used"));
            long heapMax      = parseLong(m.get("heap_max"));
            int threadCount   = parseInt(m.get("thread_count"));
            long diskUsed     = parseLong(m.get("disk_used"));
            long diskTotal    = parseLong(m.get("disk_total"));

            // epoch milliseconds → PostgreSQL TIMESTAMPTZ 변환
            // timestamp가 0이면 epoch 기준 저장 → 운영에서 이상치로 감지 가능
            String timestampIso = java.time.Instant
                    .ofEpochMilli(currentTimestamp)
                    .toString();

            batchParams.add(new Object[]{
                    timestampIso, appName, host, ip,
                    cpuUsage, heapUsed, heapMax, threadCount,
                    diskUsed, diskTotal, 0L, 0L
            });
        }

        if (!batchParams.isEmpty()) {
            metricsRepository.saveAll(batchParams);
        }
    }

    // null/blank 방어 헬퍼 — SpanProcessor와 동일한 패턴
    // null이면 기본값 반환 → 파싱 오류로 PEL 무한 잔류 방지
    // 기본값 0은 운영에서 이상치로 감지 가능 (정상 수집값은 0이 아님)
    private long parseLong(String val) {
        if (val == null || val.isBlank()) return 0L;
        return Long.parseLong(val);
    }

    private double parseDouble(String val) {
        if (val == null || val.isBlank()) return 0.0;
        return Double.parseDouble(val);
    }

    private int parseInt(String val) {
        if (val == null || val.isBlank()) return 0;
        return Integer.parseInt(val);
    }

}