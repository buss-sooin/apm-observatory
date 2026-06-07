package com.apm.observatory.collectorserver.processor;

import com.apm.observatory.collectorserver.processor.repository.MetricsRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis Stream에서 받은 메트릭 메시지를 저장 형태로 바꿔 batch 저장하는 컴포넌트.
 *
 * <p>메트릭은 span과 달리 트리 조립 같은 가공이 없다. 컬럼 값을 파싱하고 timestamp를
 * ISO-8601로 바꾼 뒤 {@link MetricsRepository}로 넘기는 단계만 거친다.
 *
 * <p>외부 경계에서 온 문자열은 null·blank 가능성을 가정하고 숫자 파싱을 기본값으로
 * 방어한다. 파싱 예외로 메시지가 PEL에 무한 잔류하는 것을 막기 위함이다. 기본값 0은
 * 정상 수집값과 구분되어 운영에서 이상치로 드러난다.
 */
@Component
public class MetricsProcessor {

    private final MetricsRepository metricsRepository;

    public MetricsProcessor(MetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    /**
     * 메트릭 메시지 묶음을 파싱·변환해 batch 저장한다.
     *
     * @param messages 한 폴링에서 받은 메트릭 메시지 묶음(컬럼-값 매핑)
     */
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

            // epoch milliseconds → PostgreSQL TIMESTAMPTZ 변환. timestamp가 0이면
            // epoch 기준으로 저장되어 운영에서 이상치로 드러난다.
            String timestampIso = java.time.Instant
                    .ofEpochMilli(currentTimestamp)
                    .toString();

            // 마지막 두 컬럼(disk_read_bytes, disk_write_bytes)은 0L 고정. disk IO 누적값은
            // Java 표준 API로 얻을 수 없어 agent가 0으로 보내고, 수집 서버도 0 그대로 저장한다.
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
