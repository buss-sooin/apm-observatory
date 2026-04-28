package com.apm.observatory.collectorserver.consumer;

import com.apm.observatory.collectorserver.config.CollectorConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class AbstractStreamConsumer {

    protected final StringRedisTemplate redisTemplate;

    protected AbstractStreamConsumer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 하위 클래스가 제공하는 값 — Template Method Pattern 추상 메서드
    protected abstract String streamKey();
    protected abstract String logPrefix();
    protected abstract void processMessages(List<Map<String, String>> messages);

    // 신규 추상 메서드 — DLQ Stream 키 제공
    // 재시도 초과 메시지를 출처별로 분리 보관하기 위해 자식이 직접 정의
    // MetricsConsumer → "stream:metrics:dead"
    // SpanConsumer    → "stream:spans:dead"
    // LogConsumer     → "stream:logs:dead"
    protected abstract String deadLetterStreamKey();

    // flushExpired — SpanConsumer만 override, 나머지는 no-op
    public void flushExpired() {}

    // 수집 서버 시작 시 Consumer Group 생성 시도
    // 정상 Stream Group + DLQ Stream Group 동시 초기화
    // MKSTREAM: 스트림이 없으면 자동 생성 → gateway보다 먼저 실행돼도 안전
    // 재시작/장애복구 시 이미 존재하면(BUSYGROUP) 그대로 사용
    @PostConstruct
    public void initConsumerGroup() {
        createGroup(streamKey(), CollectorConfig.GROUP_NAME);
        createGroup(deadLetterStreamKey(), CollectorConfig.DLQ_GROUP_NAME);
    }

    // Consumer Group 생성 공통 로직
    // BUSYGROUP: 이미 존재 → 정상 (재시작/장애복구)
    // 그 외 예외: 로그만 남기고 앱은 계속 기동
    //             consume() 실행 시 NOGROUP 감지 → initConsumerGroup() 재시도 → 자동 복구
    private void createGroup(String streamKey, String groupName) {
        try {
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.execute("XGROUP",
                        "CREATE".getBytes(),
                        streamKey.getBytes(),
                        groupName.getBytes(),
                        "0".getBytes(),
                        "MKSTREAM".getBytes()
                );
                return null;
            });
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.info("{} [{}] Consumer Group 이미 존재", logPrefix(), groupName);
            } else {
                log.warn("{} [{}] Consumer Group 초기화 실패: {}", logPrefix(), groupName, e.getMessage());
            }
        }
    }

    // 5초마다 스트림 폴링
    // NOGROUP 에러 시 — 스트림 미생성 정상 대기 상태로 간주
    // Gateway가 첫 데이터를 보내면 스트림 자동 생성 → initConsumerGroup() 재시도 → 자동 복구
    public void consume() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(CollectorConfig.GROUP_NAME, CollectorConfig.CONSUMER_NAME),
                    StreamReadOptions.empty()
                            .count(CollectorConfig.BATCH_SIZE)
                            .block(Duration.ofMillis(CollectorConfig.POLL_TIMEOUT_MS)),
                    StreamOffset.create(streamKey(), ReadOffset.lastConsumed())
            );

            if (records == null || records.isEmpty()) return;

            processAndAcknowledge(records);

        } catch (RedisSystemException e) {
            if (e.getMessage() != null && e.getMessage().contains("NOGROUP")) {
                log.info("{} 스트림 대기 중... (Gateway 데이터 수신 후 자동 복구)", logPrefix());
                initConsumerGroup();
            } else {
                log.error("{} Redis 오류: {}", logPrefix(), e.getMessage());
            }
        }
    }

    // PEL 재처리
    // 5분 이상 지난 메시지 재시도
    // MAX_RETRY_COUNT 초과 시 → DLQ Stream으로 이동 + 정상 PEL ACK
    // 이유: 복구 불가능한 메시지가 PEL에 영원히 잔류하는 것을 방지
    //       출처별 DLQ Group으로 분리해서 운영자가 추적 가능
    public void retryPending() {
        try {
            PendingMessages pendingMessages = redisTemplate.opsForStream().pending(
                    streamKey(),
                    Consumer.from(CollectorConfig.GROUP_NAME, CollectorConfig.CONSUMER_NAME),
                    Range.unbounded(),
                    CollectorConfig.BATCH_SIZE
            );

            if (pendingMessages == null || pendingMessages.isEmpty()) return;

            pendingMessages.stream()
                    .filter(p -> p.getElapsedTimeSinceLastDelivery().toMillis() > 300_000L)
                    .forEach(p -> {
                        // 재시도 횟수 초과 → DLQ로 이동
                        if (p.getTotalDeliveryCount() > CollectorConfig.MAX_RETRY_COUNT) {
                            moveToDeadLetter(p.getId());
                            return;
                        }

                        // 재시도 횟수 이내 → 재처리 시도
                        List<MapRecord<String, Object, Object>> claimed =
                                redisTemplate.opsForStream().claim(
                                        streamKey(),
                                        CollectorConfig.GROUP_NAME,
                                        CollectorConfig.CONSUMER_NAME,
                                        Duration.ofMinutes(5),
                                        p.getId()
                                );

                        if (claimed == null || claimed.isEmpty()) return;

                        processAndAcknowledge(claimed);
                    });
        } catch (Exception e) {
            log.warn("{} PEL 재처리 스킵: {}", logPrefix(), e.getMessage());
        }
    }

    // 재시도 초과 메시지 → DLQ Stream 이동
    // 1. 원본 메시지 조회 (XRANGE)
    // 2. DLQ Stream에 XADD (출처 stream 키 포함)
    // 3. 정상 PEL에서 ACK → 제거
    // 의도: 정상 PEL에서 제거해야 retryPending()이 같은 메시지를 무한 반복하지 않음
    private void moveToDeadLetter(RecordId recordId) {
        try {
            // 원본 메시지 조회
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().range(
                    streamKey(),
                    Range.closed(recordId.getValue(), recordId.getValue())
            );

            if (records == null || records.isEmpty()) {
                // 원본 메시지가 이미 없으면 ACK만 처리
                redisTemplate.opsForStream().acknowledge(streamKey(), CollectorConfig.GROUP_NAME, recordId);
                return;
            }

            // DLQ Stream에 메시지 이동
            // "source_stream" 필드 추가 → 어느 스트림에서 왔는지 추적 가능
            MapRecord<String, Object, Object> original = records.get(0);
            Map<String, String> dlqMessage = new HashMap<>();
            original.getValue().forEach((k, v) -> dlqMessage.put(k.toString(), v.toString()));
            dlqMessage.put("source_stream", streamKey());
            dlqMessage.put("original_id", recordId.getValue());

            redisTemplate.opsForStream().add(
                    StreamRecords.newRecord()
                            .in(deadLetterStreamKey())
                            .ofMap(dlqMessage)
            );

            // 정상 PEL에서 ACK → 제거
            redisTemplate.opsForStream().acknowledge(streamKey(), CollectorConfig.GROUP_NAME, recordId);

            log.error("{} DLQ 이동 완료: {} → {}", logPrefix(), recordId, deadLetterStreamKey());

        } catch (Exception e) {
            log.error("{} DLQ 이동 실패: {} - {}", logPrefix(), recordId, e.getMessage());
        }
    }

    // records → Map 변환 → processMessages() → ACK
    // consume() 과 retryPending() 공통 로직
    private void processAndAcknowledge(List<MapRecord<String, Object, Object>> records) {
        List<Map<String, String>> messages = records.stream()
                .map(record -> {
                    Map<String, String> map = new HashMap<>();
                    record.getValue().forEach((k, v) -> map.put(k.toString(), v.toString()));
                    return map;
                })
                .toList();

        try {
            processMessages(messages);

            // 저장 성공 후 ACK — PEL에서 제거
            // 저장 실패 시 ACK 안 함 → PEL에 남아서 retryPending()이 재처리
            records.forEach(record ->
                    redisTemplate.opsForStream().acknowledge(
                            streamKey(),
                            CollectorConfig.GROUP_NAME,
                            record.getId()
                    )
            );

            // 파이프라인 흐름 가시화 — 처리 완료 로그
            log.info("{} {}건 처리 완료 → DB 저장", logPrefix(), records.size());

        } catch (Exception e) {
            // 예상치 못한 예외 — ACK 안 함 → PEL에 남아서 retryPending()이 재처리
            log.error("{} 저장 실패, PEL 재처리 대기: {} - {}", logPrefix(), e.getClass().getName(), e.getMessage(), e);
        }
    }

}