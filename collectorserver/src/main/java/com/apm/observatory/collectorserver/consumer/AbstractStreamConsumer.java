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

/**
 * Redis Streams Consumer Group 기반 데이터 수집의 공통 골격을 정의하는 추상 클래스.
 *
 * <h2>Template Method 패턴 적용</h2>
 *
 * <p>이 클래스는 Redis Streams 폴링·PEL 재처리·DLQ 이동의 공통 흐름을 골격으로 가지고,
 * 처리 단위와 처리 시점은 구현 클래스가 결정하는 Template Method 패턴으로 설계됐다.
 *
 * <h3>골격 메서드 (구현 클래스가 흐름을 바꾸지 않음)</h3>
 * <ul>
 *   <li>{@link #consume()} — 5초 주기 폴링. records 읽기 → 구현 클래스 처리 호출 → NOGROUP 자동 복구</li>
 *   <li>{@link #retryPending()} — 1분 주기 PEL 점검. 5분 임계 도래 메시지 재처리 또는 DLQ 이동</li>
 *   <li>{@link #moveToDeadLetter(RecordId)} — 재시도 횟수 초과 메시지를 DLQ Stream으로 이동</li>
 *   <li>{@link #initConsumerGroup()} — 빈 초기화 시 Consumer Group 자동 생성</li>
 * </ul>
 *
 * <h3>구현 클래스가 채우는 자리</h3>
 * <ul>
 *   <li>{@link #streamKey()}, {@link #deadLetterStreamKey()}, {@link #logPrefix()}
 *       — 데이터 종류 식별자</li>
 *   <li>{@link #processMessages(List)} — 받은 메시지를 어떻게 처리할지. 처리 후
 *       {@link #acknowledge(List)} 호출까지 구현 클래스의 책임</li>
 *   <li>{@link #flushExpired()} — Span 등 buffer 경유 처리가 필요한 구현 클래스만 override</li>
 * </ul>
 *
 * <h3>구현 클래스가 호출할 수 있는 헬퍼 (Hook + Helper 변형)</h3>
 * <ul>
 *   <li>{@link #acknowledge(List)} — Redis Stream PEL에서 메시지 제거</li>
 *   <li>{@link #addToDeadLetterStream(Map)} — DLQ Stream에 메시지를 옮기는 XADD</li>
 *   <li>{@link #toStringMaps(List)} — Redis MapRecord 목록을 컬럼-값 문자열 Map 목록으로 변환</li>
 * </ul>
 *
 * <h2>ACK 시점에 대한 구현 클래스의 책임</h2>
 *
 * <p>이 클래스는 ACK 호출을 직접 하지 않는다. 구현 클래스의 처리 모델에 따라 ACK
 * 시점이 다를 수 있기 때문이다. 구현 클래스는 처리 완료 시점에 반드시
 * {@link #acknowledge(List)}를 호출해야 한다. 호출이 누락되면 메시지가 PEL에 남아
 * {@link #retryPending()}이 재시도를 반복하다 결국 DLQ로 이동시킨다.
 *
 * <ul>
 *   <li>도착 즉시 saveAll까지 끝나는 구현 클래스(LogConsumer, MetricsConsumer):
 *       {@link #processMessages(List)} 내부에서 saveAll 직후 acknowledge 호출</li>
 *   <li>buffer 경유 처리 구현 클래스(SpanConsumer):
 *       {@link #flushExpired()} 안에서 trace 종료 판정과 saveAll 완료 후 acknowledge 호출</li>
 * </ul>
 *
 * <h2>DLQ 이동 메커니즘 분해</h2>
 *
 * <p>DLQ Stream에 메시지를 옮기는 XADD 호출은 {@link #addToDeadLetterStream(Map)}에
 * 모여 있다. {@link #moveToDeadLetter(RecordId)}는 PEL에서 재시도 초과로 옮기는 자리에서
 * 이 헬퍼를 사용하고, SpanConsumer는 buffer에서 직접 옮기는 자리에서 같은 헬퍼를
 * 사용한다. DLQ XADD 로직이 한 자리에 모여 변경 영향 범위가 좁다.
 */
@Slf4j
public abstract class AbstractStreamConsumer {

    protected final StringRedisTemplate redisTemplate;

    protected AbstractStreamConsumer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 구현 클래스가 폴링할 정상 Redis Stream 키를 제공한다.
     */
    protected abstract String streamKey();

    /**
     * 구현 클래스가 사용할 로그 prefix를 제공한다. 컴포넌트 식별용.
     */
    protected abstract String logPrefix();

    /**
     * 구현 클래스가 재시도 초과 메시지를 옮길 DLQ Stream 키를 제공한다.
     *
     * <p>출처별로 다른 DLQ Stream을 사용해 운영자가 출처를 식별할 수 있게 한다.
     * <ul>
     *   <li>MetricsConsumer → {@code "stream:metrics:dead"}</li>
     *   <li>SpanConsumer → {@code "stream:spans:dead"}</li>
     *   <li>LogConsumer → {@code "stream:logs:dead"}</li>
     * </ul>
     */
    protected abstract String deadLetterStreamKey();

    /**
     * 구현 클래스의 메시지 처리 단계. records를 받아 처리하고 처리 완료 시점에
     * 반드시 {@link #acknowledge(List)}를 호출해야 한다. 호출이 누락되면
     * 메시지가 PEL에 남아 {@link #retryPending()}이 재처리하게 된다.
     *
     * <p>records → 문자열 Map 변환은 {@link #toStringMaps(List)} 헬퍼를 사용하면 된다.
     *
     * @param records Redis Stream에서 읽어온 메시지 묶음
     */
    protected abstract void processMessages(List<MapRecord<String, Object, Object>> records);

    /**
     * buffer 경유 처리가 필요한 구현 클래스가 override 하는 hook. 기본 동작은 no-op.
     *
     * <p>현재 SpanConsumer만 override 한다. trace 종료 판정과 saveAll, ACK 호출,
     * DLQ 이동을 이 메서드 안에서 직접 수행한다.
     */
    public void flushExpired() {}

    /**
     * 수집 서버 시작 시 정상 Stream과 DLQ Stream의 Consumer Group을 자동 생성한다.
     *
     * <p>MKSTREAM 옵션으로 Stream이 없을 때도 동시에 생성하므로 gateway보다 먼저
     * 실행돼도 안전하다. 이미 존재하는 Group이면 BUSYGROUP 응답을 받고 그대로 사용한다.
     */
    @PostConstruct
    public void initConsumerGroup() {
        createGroup(streamKey(), CollectorConfig.GROUP_NAME);
        createGroup(deadLetterStreamKey(), CollectorConfig.DLQ_GROUP_NAME);
    }

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

    /**
     * 5초 주기 폴링 진입점. 구현 클래스의 {@link #processMessages(List)}로 records를 넘긴다.
     *
     * <p>NOGROUP 에러는 Stream 미생성 정상 대기 상태로 간주한다. Gateway가 첫 데이터를
     * 보내면 Stream이 자동 생성되고, {@link #initConsumerGroup()}을 재호출해 Group을
     * 만든 후 다음 폴링에서 정상화된다.
     */
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

            dispatchToProcessor(records);

        } catch (RedisSystemException e) {
            if (e.getMessage() != null && e.getMessage().contains("NOGROUP")) {
                log.info("{} 스트림 대기 중... (Gateway 데이터 수신 후 자동 복구)", logPrefix());
                initConsumerGroup();
            } else {
                log.error("{} Redis 오류: {}", logPrefix(), e.getMessage());
            }
        }
    }

    /**
     * 1분 주기 PEL 점검. 마지막 전달 후 5분 이상 지난 메시지를 재시도하거나 DLQ로 옮긴다.
     *
     * <p>{@link CollectorConfig#MAX_RETRY_COUNT}를 초과한 메시지는
     * {@link #moveToDeadLetter(RecordId)}로 보내 PEL에서 영구 잔류하는 것을 막는다.
     * 임계 이내 메시지는 {@code XCLAIM}으로 다시 가져와 구현 클래스의 처리를 재시도한다.
     */
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
                        if (p.getTotalDeliveryCount() > CollectorConfig.MAX_RETRY_COUNT) {
                            moveToDeadLetter(p.getId());
                            return;
                        }

                        List<MapRecord<String, Object, Object>> claimed =
                                redisTemplate.opsForStream().claim(
                                        streamKey(),
                                        CollectorConfig.GROUP_NAME,
                                        CollectorConfig.CONSUMER_NAME,
                                        Duration.ofMinutes(5),
                                        p.getId()
                                );

                        if (claimed == null || claimed.isEmpty()) return;

                        dispatchToProcessor(claimed);
                    });
        } catch (Exception e) {
            log.warn("{} PEL 재처리 스킵: {}", logPrefix(), e.getMessage());
        }
    }

    /**
     * 재시도 초과 메시지 한 건을 DLQ Stream으로 옮긴다. XRANGE로 원본을 조회한 뒤
     * {@link #addToDeadLetterStream(Map)}으로 옮기고 정상 PEL에서 acknowledge 한다.
     *
     * <p>원본 메시지가 이미 사라진 경우 acknowledge만 처리해 PEL을 정리한다.
     */
    private void moveToDeadLetter(RecordId recordId) {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().range(
                    streamKey(),
                    Range.closed(recordId.getValue(), recordId.getValue())
            );

            if (records == null || records.isEmpty()) {
                redisTemplate.opsForStream().acknowledge(streamKey(), CollectorConfig.GROUP_NAME, recordId);
                return;
            }

            MapRecord<String, Object, Object> original = records.get(0);
            Map<String, String> dlqMessage = new HashMap<>();
            original.getValue().forEach((k, v) -> dlqMessage.put(k.toString(), v.toString()));
            dlqMessage.put("original_id", recordId.getValue());

            addToDeadLetterStream(dlqMessage);

            redisTemplate.opsForStream().acknowledge(streamKey(), CollectorConfig.GROUP_NAME, recordId);

            log.error("{} DLQ 이동 완료: {} → {}", logPrefix(), recordId, deadLetterStreamKey());

        } catch (Exception e) {
            log.error("{} DLQ 이동 실패: {} - {}", logPrefix(), recordId, e.getMessage());
        }
    }

    /**
     * {@link #consume()}과 {@link #retryPending()}이 records를 받아 구현 클래스의
     * {@link #processMessages(List)}로 넘기는 공통 진입점. 처리 중 예외가 발생하면
     * 로그만 남기고 메시지는 PEL에 그대로 둬서 {@link #retryPending()}이 재처리하게 한다.
     *
     * <p>ACK 호출은 구현 클래스 책임이다. 이 메서드는 구현 클래스 호출만 담당한다.
     */
    private void dispatchToProcessor(List<MapRecord<String, Object, Object>> records) {
        try {
            processMessages(records);
        } catch (Exception e) {
            log.error("{} 처리 실패, PEL 재처리 대기: {} - {}",
                    logPrefix(), e.getClass().getName(), e.getMessage(), e);
        }
    }

    /**
     * 구현 클래스가 호출하는 헬퍼. recordId 목록을 정상 Stream Consumer Group에서 acknowledge 한다.
     *
     * <p>acknowledge 한 메시지는 PEL에서 제거되어 {@link #retryPending()}의 재시도
     * 대상에서 빠진다. 빈 목록이면 아무 동작도 하지 않는다.
     *
     * @param recordIds acknowledge 할 메시지 식별자 모음
     */
    protected void acknowledge(List<RecordId> recordIds) {
        if (recordIds.isEmpty()) return;
        recordIds.forEach(recordId ->
                redisTemplate.opsForStream().acknowledge(
                        streamKey(),
                        CollectorConfig.GROUP_NAME,
                        recordId
                )
        );
    }

    /**
     * 구현 클래스가 호출하는 헬퍼. 한 건의 메시지를 DLQ Stream에 XADD 한다. source_stream
     * 필드를 자동으로 부착해 어느 출처 Stream에서 왔는지 운영자가 추적할 수 있게 한다.
     *
     * <p>호출자는 이 호출 후 정상 Stream의 recordId를 {@link #acknowledge(List)}로
     * 별도 처리해야 한다.
     *
     * @param message DLQ로 옮길 메시지의 컬럼-값 매핑
     */
    protected void addToDeadLetterStream(Map<String, String> message) {
        Map<String, String> dlqMessage = new HashMap<>(message);
        dlqMessage.put("source_stream", streamKey());

        redisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .in(deadLetterStreamKey())
                        .ofMap(dlqMessage)
        );
    }

    /**
     * 구현 클래스가 호출하는 헬퍼. Redis MapRecord 목록을 컬럼-값 문자열 Map 목록으로 변환한다.
     * Redis가 반환하는 Object 타입의 키·값을 문자열로 풀어준다.
     *
     * @param records Redis Stream에서 읽어온 원본 records
     * @return 각 record의 value를 문자열 Map으로 변환한 목록
     */
    protected List<Map<String, String>> toStringMaps(List<MapRecord<String, Object, Object>> records) {
        return records.stream()
                .map(record -> {
                    Map<String, String> map = new HashMap<>();
                    record.getValue().forEach((k, v) -> map.put(k.toString(), v.toString()));
                    return map;
                })
                .toList();
    }

}