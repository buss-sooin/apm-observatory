package com.apm.observatory.agent.queue;

import com.apm.common.proto.MonitoringProto.LogData;
import com.apm.common.proto.MonitoringProto.MetricsData;
import com.apm.common.proto.MonitoringProto.SpanData;

import java.util.List;

// 퍼사드 패턴 인터페이스
// GoF 의도: 복잡한 서브시스템(ArrayBlockingQueue + QueueItem + DataType)을
//           단순한 고수준 인터페이스로 노출
// Advice는 이 인터페이스만 알고 내부 구현을 몰라도 됨
// 큐 구현체 교체 시 Advice 코드 무변경 보장
public interface DataQueue {

    // Advice가 사용하는 진입점 (퍼사드)
    // offer() 전략: 꽉 차면 즉시 false 반환 → 타겟 앱 스레드 블로킹 없음
    void offerMetrics(MetricsData data);
    void offerSpan(SpanData data);
    void offerLog(LogData data);
    int getQueueSize();
    long getDropCount();

    // QueueWorker가 배치로 꺼낼 때 사용
    // drainTo() 선택 이유: lock 1번으로 N건을 한 번에 꺼냄
    // poll() N번보다 lock 비용 절감
    // maxItems: 한 번의 배치에서 꺼낼 최대 건수
    // 반환값: 실제로 꺼낸 건수
    int drainAll(List<QueueItem> target, int maxItems);

}