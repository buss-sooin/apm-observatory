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

/**
 * Protobuf 배치를 Lettuce 비동기 방식으로 Redis Streams에 XADD로 발행한다.
 *
 * <p>저장 형식은 Protobuf getter를 {@code Map<String, String>}로 필드별 변환한 것이다.
 * 고빈도 외부 전송 구간에 해당하는 에이전트 → gateway 구간은 Protobuf 바이너리를 유지하고,
 * 내부 전송 구간에 해당하는 gateway → Redis 구간은 JSON 필드별 저장을 유지했다.
 * 수집 서버는 이 {@code Map<String, String>}의 키로 필드에 직접 접근한다.
 */
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
        // Lettuce 연결은 thread-safe라 단일 연결을 여러 gRPC 스레드가 공유한다
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

    /**
     * 타입별 발행 공통 로직. 스트림 키와 변환 함수만 다르다.
     *
     * @param toMap 항목을 {@code Map<String, String>}로 바꾸는 함수. 호출자가
     *              {@code this::metricsToMap} 같은 메서드 레퍼런스로 변환 로직을 정한다.
     */
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

    /**
     * Protobuf getter를 {@code Map<String, String>}로 변환한다. 여기서 쓰는 키 이름은
     * 수집 서버 Processor가 {@code m.get("key")}로 꺼내는 키와 정확히 일치해야 한다.
     */
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
