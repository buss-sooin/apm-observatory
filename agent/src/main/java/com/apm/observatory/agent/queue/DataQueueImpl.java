package com.apm.observatory.agent.queue;

import com.apm.common.proto.MonitoringProto.LogData;
import com.apm.common.proto.MonitoringProto.MetricsData;
import com.apm.common.proto.MonitoringProto.SpanData;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

// DataQueue 퍼사드 구현체
// 내부 복잡성(ArrayBlockingQueue + QueueItem 래핑)을 숨기고
// Advice에게 타입별 단순 메서드만 노출
public class DataQueueImpl implements DataQueue {

    // 고정 크기 배열 기반 큐
    // ArrayBlockingQueue 선택 이유:
    //   - 고정 크기로 메모리 상한 보장
    //   - LinkedBlockingQueue보다 메모리 효율적 (노드 객체 할당 없음)
    //   - 지금 수집량에서는 표준 구현으로 충분하다고 판단함
    // 큐 경합이 심해진다면 잠금 없이 동시 접근을 처리하는 방식으로 바꾸는 게 나을 것 같음
    private final ArrayBlockingQueue<QueueItem> queue;
    // 여러 요청 스레드(Advice)가 동시에 offer() 호출 → 원자적 증가 필요
    private final AtomicLong dropCount = new AtomicLong(0);

    public DataQueueImpl(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    @Override
    public void offerMetrics(MetricsData data) {
        // offer(): 꽉 차면 false 반환 후 즉시 복귀 (드롭 전략)
        // put()은 블로킹 → 타겟 앱 영향 금지 원칙 위반
        // offer() false = 큐 꽉 참 → 드롭 발생
        if (!queue.offer(new QueueItem(DataType.METRICS, data))) {
            dropCount.incrementAndGet();
        }
    }

    @Override
    public void offerSpan(SpanData data) {
        if (!queue.offer(new QueueItem(DataType.SPAN, data))) {
            dropCount.incrementAndGet();
        }
    }

    @Override
    public void offerLog(LogData data) {
        if (!queue.offer(new QueueItem(DataType.LOG, data))) {
            dropCount.incrementAndGet();
        }
    }

    @Override
    public int getQueueSize() {
        return queue.size();
    }

    @Override
    public long getDropCount() {
        return dropCount.get();
    }

    @Override
    public int drainAll(List<QueueItem> target, int maxItems) {
        // lock 1번으로 최대 maxItems건을 한 번에 꺼냄
        // poll() N번 반복보다 lock 획득/해제 비용 절감
        return queue.drainTo(target, maxItems);
    }

}