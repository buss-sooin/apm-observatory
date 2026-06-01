package com.apm.observatory.agent.queue;

import com.apm.common.proto.MonitoringProto.LogData;
import com.apm.common.proto.MonitoringProto.MetricsData;
import com.apm.common.proto.MonitoringProto.SpanData;
import org.jctools.queues.MpscArrayQueue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advice 후킹 측이 호출하는 큐 퍼사드.
 *
 * <p>타깃 앱의 요청 스레드(Advice 4종 + MetricsCollector + GrpcLogbackAppender)가
 * 데이터를 offer하고, QueueWorker 한 스레드만 drain한다. 이 producer 다수 ·
 * consumer 1개 구조에 정확히 들어맞는 JCTools {@link MpscArrayQueue}를 내부 자료
 * 구조로 둔다. 자리 잡기는 producerIndex CAS, 데이터 쓰기는 각자 다른 슬롯에서
 * 일어나므로 {@link java.util.concurrent.ArrayBlockingQueue}의 단일 ReentrantLock
 * 경합과 park/unpark 비용이 사라진다.
 *
 * <p>큐가 가득이면 offer가 false를 반환하고 데이터는 드롭된다. 타깃 앱이 멈추지
 * 않도록 백프레셔를 producer 측으로 흘리지 않는 정책이다. 완전한 유실 방지는 후단
 * Redis Streams + AOF가 담당한다.
 *
 * <p>{@link DataType}별로 메서드를 나눠 호출 측이 {@link QueueItem} 구성 책임을
 * 모르게 한다.
 */
public class DataQueueImpl implements DataQueue {

    private final MpscArrayQueue<QueueItem> queue;
    private final AtomicLong dropCount = new AtomicLong(0);

    public DataQueueImpl(int capacity) {
        this.queue = new MpscArrayQueue<>(capacity);
    }

    /**
     * 메트릭 데이터를 큐에 적재한다. 가득이면 드롭하고 dropCount를 증가시킨다.
     */
    @Override
    public void offerMetrics(MetricsData data) {
        if (!queue.offer(new QueueItem(DataType.METRICS, data))) {
            dropCount.incrementAndGet();
        }
    }

    /**
     * span 데이터를 큐에 적재한다. 가득이면 드롭하고 dropCount를 증가시킨다.
     */
    @Override
    public void offerSpan(SpanData data) {
        if (!queue.offer(new QueueItem(DataType.SPAN, data))) {
            dropCount.incrementAndGet();
        }
    }

    /**
     * 로그 데이터를 큐에 적재한다. 가득이면 드롭하고 dropCount를 증가시킨다.
     */
    @Override
    public void offerLog(LogData data) {
        if (!queue.offer(new QueueItem(DataType.LOG, data))) {
            dropCount.incrementAndGet();
        }
    }

    /** 현재 큐에 적재된 항목 수. */
    @Override
    public int getQueueSize() {
        return queue.size();
    }

    /** 누적 드롭 수. */
    @Override
    public long getDropCount() {
        return dropCount.get();
    }

    /**
     * 큐에서 최대 {@code maxItems}개를 꺼내 {@code target}에 채워 넣는다.
     *
     * <p>{@link MpscArrayQueue}는 {@link java.util.Queue#drainTo}를 구현하지 않고
     * {@link org.jctools.queues.MessagePassingQueue#drain} 시그니처로
     * {@code Consumer<E>}를 받는다. {@code target::add}를 Consumer로 넘겨
     * 내부 relaxedPoll 루프가 List에 직접 add 하도록 위임한다.
     *
     * <p>MPSC 큐의 drain은 단일 consumer 스레드 전용이므로 QueueWorker 외 다른
     * 스레드가 이 메서드를 호출하면 안 된다.
     *
     * @param target   꺼낸 항목을 담을 List
     * @param maxItems 한 번에 꺼낼 최대 건수
     * @return 실제로 꺼낸 항목 수
     */
    @Override
    public int drainAll(List<QueueItem> target, int maxItems) {
        return queue.drain(target::add, maxItems);
    }

}