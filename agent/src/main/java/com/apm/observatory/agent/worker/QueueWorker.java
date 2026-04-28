package com.apm.observatory.agent.worker;

import com.apm.common.proto.MonitoringProto;
import com.apm.observatory.agent.queue.DataQueue;
import com.apm.observatory.agent.queue.QueueItem;
import com.apm.observatory.agent.config.AgentConfig;
import com.apm.observatory.agent.sender.DataSender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// 생산자-소비자 패턴의 소비자 역할
// DataQueue에서 배치로 꺼내 → Protobuf 배치 조립 → DataSender 전송
// 변환 책임(QueueItem → Protobuf 배치)은 이 클래스가 보유
// 전송 책임은 DataSender에 위임 (단일 책임 원칙)
public class QueueWorker {

    private final DataQueue queue;
    private final DataSender sender;
    private final ExecutorService executor;

    // volatile 선택 이유:
    //   AgentMain ShutdownHook 스레드(쓰기)와 QueueWorker 스레드(읽기)가
    //   서로 다른 CPU 코어의 캐시를 사용할 수 있음
    //   volatile 없으면 ShutdownHook의 false 변경이 QueueWorker에 즉시 반영 안 될 수 있음
    //   volatile은 항상 메인 메모리에서 직접 읽고 써서 즉시 반영 보장
    private volatile boolean running = false;

    public QueueWorker(DataQueue queue, DataSender sender) {
        this.queue = queue;
        this.sender = sender;
        // ThreadFactory로 스레드 이름과 데몬 여부 직접 제어
        // 기본 팩토리는 이름/데몬 설정 불가
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "queue-worker");
            // 데몬 스레드: 타겟 앱의 일반 스레드가 모두 종료되면 JVM과 함께 종료
            // 일반 스레드면 QueueWorker가 살아있는 한 JVM이 종료되지 않음
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        running = true;
        executor.submit(this::run);
    }

    public void stop() {
        running = false;
        // 새 작업 제출 차단, 현재 실행 중인 배치 처리는 완료 대기
        executor.shutdown();
        try {
            // 최대 SHUTDOWN_TIMEOUT_SEC 동안 graceful 종료 대기
            if (!executor.awaitTermination(
                    AgentConfig.SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                // 시간 초과 시 강제 종료
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            // awaitTermination 대기 중 외부 강제 종료 신호
            // graceful 종료 포기, 강제 종료로 전환
            executor.shutdownNow();
        }
        // 루프 탈출 후 큐에 남은 데이터 마지막 전송
        // 종료 시 큐 잔여 데이터 유실 방지
        flushRemaining();
    }

    private void run() {
        while (running) {
            List<QueueItem> batch = new ArrayList<>();
            int count = queue.drainAll(batch, AgentConfig.BATCH_SIZE);

            if (count == 0) {
                // 큐가 비어있음 → 잠깐 대기 후 재확인
                // sleep 없이 while 루프 시 CPU 100% 점유
                try {
                    Thread.sleep(AgentConfig.IDLE_WAIT_MS);
                } catch (InterruptedException e) {
                    // sleep 중 종료 신호 → 루프 탈출
                    break;
                }
                continue;
            }

            send(batch);
        }
    }

    // 변환 책임: QueueItem → Protobuf 배치 조립
    // DataSender는 전송만 담당 (단일 책임 원칙)
    // switch 화살표 문법: Java 14+ 정식 지원, break 없이 각 case 독립 실행
    // try-catch 이유: GrpcSenderImpl.sendWithRetry()가 StatusRuntimeException은 잡지만
    //                그 외 예외(네트워크 레벨 등)는 전파됨
    //                예외가 run() 루프 밖으로 전파되면 QueueWorker 전체가 죽어버림
    //                타겟 앱은 살아있는데 에이전트 전송만 멈추는 상황 방지
    private void send(List<QueueItem> batch) {
        MonitoringProto.MetricsBatch.Builder metricsBuilder = MonitoringProto.MetricsBatch.newBuilder();
        MonitoringProto.SpanBatch.Builder spanBuilder       = MonitoringProto.SpanBatch.newBuilder();
        MonitoringProto.LogBatch.Builder logBuilder         = MonitoringProto.LogBatch.newBuilder();

        for (QueueItem item : batch) {
            switch (item.type()) {
                case METRICS -> metricsBuilder.addItems((MonitoringProto.MetricsData) item.data());
                case SPAN    -> spanBuilder.addItems((MonitoringProto.SpanData) item.data());
                case LOG     -> logBuilder.addItems((MonitoringProto.LogData) item.data());
            }
        }

        try {
            // 비어있는 배치는 GrpcSenderImpl 내부에서 전송 스킵
            sender.sendMetrics(metricsBuilder.build());
            sender.sendSpan(spanBuilder.build());
            sender.sendLog(logBuilder.build());
        } catch (Exception e) {
            // 전송 실패 — 이 배치는 드롭, run() 루프는 계속 실행
            // 타겟 앱 영향 금지 원칙: 에이전트 장애가 타겟 앱을 멈추면 안 됨
            // 완전한 유실 방지는 Redis Streams + AOF가 담당
            System.err.println("[QueueWorker] 배치 전송 실패, 드롭: " + e.getMessage());
        }
    }

    private void flushRemaining() {
        List<QueueItem> remaining = new ArrayList<>();
        // Integer.MAX_VALUE: 남은 데이터 전부 꺼냄
        queue.drainAll(remaining, Integer.MAX_VALUE);
        if (!remaining.isEmpty()) {
            send(remaining);
        }
    }

    public boolean isRunning() {
        return running;
    }

    // 즉시 drainAll() → send() 호출
    // 테스트 시 주기 대기 없이 바로 전송 강제
    public void flush() {
        List<QueueItem> batch = new ArrayList<>();
        queue.drainAll(batch, Integer.MAX_VALUE);
        if (!batch.isEmpty()) {
            send(batch);
        }
    }

}