package com.apm.observatory.agent.queue;

import com.apm.common.proto.MonitoringProto.LogData;
import com.apm.common.proto.MonitoringProto.MetricsData;
import com.apm.common.proto.MonitoringProto.SpanData;

import java.util.List;

/**
 * 큐 적재·인출을 노출하는 퍼사드 인터페이스.
 *
 * <p>내부의 큐 자료구조와 {@link QueueItem}, {@link DataType} 구성을 감추고, Advice와
 * {@link com.apm.observatory.agent.worker.QueueWorker}에는 고수준 메서드만 노출한다.
 * 호출 측은 이 인터페이스만 알면 되고, 큐 구현체를 교체해도 호출 코드는 바뀌지 않는다.
 *
 * <p>{@code offer} 계열은 큐가 가득 차면 적재를 드롭하고 호출 스레드를 블로킹하지 않는다.
 * 타깃 앱 스레드가 큐 때문에 멈추지 않게 하기 위한 정책이다.
 */
public interface DataQueue {

    void offerMetrics(MetricsData data);
    void offerSpan(SpanData data);
    void offerLog(LogData data);
    int getQueueSize();
    long getDropCount();

    /**
     * 큐에서 최대 {@code maxItems}건을 배치로 꺼내 {@code target}에 담는다. lock 한 번으로
     * 여러 건을 옮겨 {@code poll}을 반복하는 것보다 lock 비용이 적다.
     *
     * @param target   꺼낸 항목을 담을 리스트
     * @param maxItems 한 배치에서 꺼낼 최대 건수
     * @return 실제로 꺼낸 건수
     */
    int drainAll(List<QueueItem> target, int maxItems);

}