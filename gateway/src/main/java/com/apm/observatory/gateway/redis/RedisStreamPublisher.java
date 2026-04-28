package com.apm.observatory.gateway.redis;

import com.apm.common.proto.MonitoringProto;
import com.apm.observatory.gateway.config.GatewayConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Redis Streams XADD 담당
// Lettuce 비동기 방식 사용
//
// 저장 형식: Protobuf getter → Map<String, String> 필드별 변환
// Gateway → Redis 구간은 사내 네트워크이므로 JSON 필드별 저장이 적합
// 에이전트 → Gateway 구간(외부 네트워크, 고빈도)은 Protobuf 바이너리 유지
//
// 수집 서버에서 Map<String, String> 키로 직접 필드 접근
//
// 더 나아간다면 여기서 고려해야 할 것들이 있음
//   maxlen으로 스트림 크기 관리 + 만료 정책 설정
//   연결 풀 구성 고려
public class RedisStreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamPublisher.class);

    private final RedisAsyncCommands<String, String> commands;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisClient client;

    public RedisStreamPublisher() {
        RedisURI uri = RedisURI.builder()
                .withHost(GatewayConfig.REDIS_HOST)
                .withPort(GatewayConfig.REDIS_PORT)
                .build();

        this.client = RedisClient.create(uri);
        // Lettuce 연결은 thread-safe
        // 단일 연결로 여러 gRPC 스레드가 공유 가능
        // 부하가 커지면 연결을 여러 개 두는 방식이 필요할 것 같음
        this.connection = client.connect();
        this.commands = connection.async();
    }

    public void publishMetrics(MonitoringProto.MetricsBatch batch) {
        publish(GatewayConfig.STREAM_METRICS, batch.getItemsList(), this::metricsToMap);
    }

    public void publishSpans(MonitoringProto.SpanBatch batch) {
        publish(GatewayConfig.STREAM_SPANS, batch.getItemsList(), this::spanToMap);
    }

    public void publishLogs(MonitoringProto.LogBatch batch) {
        publish(GatewayConfig.STREAM_LOGS, batch.getItemsList(), this::logToMap);
    }

    // 공통 발행 로직 — 스트림 키와 변환 함수만 다름
    // Function<T, Map<String, String>>: T 타입을 받아 Map으로 변환하는 함수를 주입받음
    // 호출자가 this::metricsToMap 등 메서드 레퍼런스로 변환 로직 결정
    private <T> void publish(String streamKey, List<T> items,
                             Function<T, Map<String, String>> toMap) {
        items.forEach(item -> {
            try {
                commands.xadd(
                        streamKey,
                        XAddArgs.Builder.maxlen(100_000),
                        toMap.apply(item)
                );
            } catch (Exception e) {
                log.error("발행 실패: {}", e.getMessage());
            }
        });
    }

    // Protobuf getter → Map<String, String>
    // 수집 서버 Processor의 m.get("key") 키 이름과 정확히 일치
    private Map<String, String> metricsToMap(MonitoringProto.MetricsData item) {
        Map<String, String> map = new HashMap<>();
        map.put("timestamp", String.valueOf(item.getTimestamp()));
        map.put("app_name", item.getAppName());
        map.put("host", item.getHost());
        map.put("ip", item.getIp());
        map.put("cpu_usage", String.valueOf(item.getCpuUsage()));
        map.put("heap_used", String.valueOf(item.getHeapUsed()));
        map.put("heap_max", String.valueOf(item.getHeapMax()));
        map.put("thread_count", String.valueOf(item.getThreadCount()));
        map.put("disk_used", String.valueOf(item.getDiskUsed()));
        map.put("disk_total", String.valueOf(item.getDiskTotal()));
        map.put("disk_read_bytes", String.valueOf(item.getDiskReadBytes()));
        map.put("disk_write_bytes", String.valueOf(item.getDiskWriteBytes()));
        return map;
    }

    private Map<String, String> spanToMap(MonitoringProto.SpanData item) {
        Map<String, String> map = new HashMap<>();
        map.put("span_id", item.getSpanId());
        map.put("trace_id", item.getTraceId());
        map.put("parent_span_id", item.getParentSpanId());
        map.put("app_name", item.getAppName());
        map.put("host", item.getHost());
        map.put("span_type", item.getSpanType());
        map.put("start_time", String.valueOf(item.getStartTime()));
        map.put("end_time", String.valueOf(item.getEndTime()));
        map.put("duration_ms", String.valueOf(item.getDurationMs()));
        map.put("http_method", item.getHttpMethod());
        map.put("http_url", item.getHttpUrl());
        map.put("http_status", String.valueOf(item.getHttpStatus()));
        map.put("sql_query", item.getSqlQuery());
        map.put("external_host", item.getExternalHost());
        map.put("error", String.valueOf(item.getError()));
        map.put("error_message", item.getErrorMessage());
        return map;
    }

    private Map<String, String> logToMap(MonitoringProto.LogData item) {
        Map<String, String> map = new HashMap<>();
        map.put("timestamp", String.valueOf(item.getTimestamp()));
        map.put("app_name", item.getAppName());
        map.put("host", item.getHost());
        map.put("thread_name", item.getThreadName());
        map.put("level", item.getLevel());
        map.put("message", item.getMessage());
        map.put("trace_id", item.getTraceId());
        map.put("stack_trace", item.getStackTrace());
        return map;
    }

    public void shutdown() {
        connection.close();
        client.shutdown();
    }

}