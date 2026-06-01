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

/**
 * 생산자-소비자 패턴의 소비자 역할.
 *
 * <p>{@link DataQueue}에서 배치 단위로 꺼내 Protobuf 배치로 조립하고 {@link DataSender}로
 * 전송한다. 변환 책임은 이 클래스에 두고 전송 책임은 DataSender에 위임한다.
 *
 * <p>단일 스레드 ExecutorService로 동작한다. 큐의 consumer가 한 스레드여야 한다는 MPSC
 * 큐 제약과 맞물린다. 스레드는 데몬으로 두어 타깃 앱의 일반 스레드가 모두 종료되면
 * JVM과 함께 자연 종료되도록 한다.
 *
 * <p>큐가 비어있으면 짧게 sleep 하고 다시 확인하는 폴링 방식이다. busy-wait를 피하기
 * 위한 자리이고 sleep 간격은 {@link AgentConfig#IDLE_WAIT_MS}이다.
 *
 * <p>종료는 stop으로 트리거된다. running 플래그를 false로 내리고, 진행 중인 배치
 * 처리를 deadline 안에 끝낼 기회를 준 뒤, 큐에 남은 데이터를 flush 한다. 종료 deadline은
 * {@link AgentConfig#SHUTDOWN_TIMEOUT_SEC}.
 */
public class QueueWorker {

    private final DataQueue queue;
    private final DataSender sender;
    private final ExecutorService executor;

    /**
     * 실행 플래그. AgentMain ShutdownHook 스레드(쓰기)와 worker 스레드(읽기)가 서로
     * 다른 CPU 코어 캐시를 쓸 수 있으므로 volatile로 메인 메모리 가시성을 보장한다.
     */
    private volatile boolean running = false;

    public QueueWorker(DataQueue queue, DataSender sender) {
        this.queue = queue;
        this.sender = sender;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "queue-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /** worker 스레드를 기동한다. */
    public void start() {
        running = true;
        executor.submit(this::run);
    }

    /**
     * worker를 graceful 종료한다.
     *
     * <p>running 플래그를 내려 다음 루프 진입을 막고, executor를 shutdown해 새 작업
     * 제출을 차단한다. {@link AgentConfig#SHUTDOWN_TIMEOUT_SEC} 안에 진행 중인 배치가
     * 끝나지 않으면 shutdownNow로 전환한다. 마지막에 큐에 남은 데이터를 flushRemaining
     * 으로 한 번 더 꺼내 전송 시도한다.
     */
    public void stop() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(
                    AgentConfig.SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        flushRemaining();
    }

    /**
     * worker 메인 루프. drain → send 반복. 큐가 비어있으면 IDLE_WAIT_MS 만큼 sleep.
     */
    private void run() {
        while (running) {
            List<QueueItem> batch = new ArrayList<>();
            int count = queue.drainAll(batch, AgentConfig.BATCH_SIZE);

            if (count == 0) {
                try {
                    Thread.sleep(AgentConfig.IDLE_WAIT_MS);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            send(batch);
        }
    }

    /**
     * 배치로 꺼낸 {@link QueueItem} 목록을 타입별 Protobuf 배치로 조립해 전송한다.
     *
     * <p>변환 책임은 이 자리에 보유하고 전송은 {@link DataSender}에 위임한다. switch
     * 화살표 문법(Java 14+)으로 case 별 독립 실행 한다.
     *
     * <p>try-catch는 protobuf 빌더 예외 등 비정상 케이스에서 run 루프가 죽지 않도록
     * 둔다. AsyncStub은 RPC 실패를 worker 스레드로 던지지 않으므로(콜백 스레드로 감)
     * 정상 경로의 RPC 에러는 이 catch에 잡히지 않는다. 타깃 앱이 살아있는데 에이전트
     * 전송만 멈추는 상황을 방지하기 위한 보호 자리다.
     */
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
            sender.sendMetrics(metricsBuilder.build());
            sender.sendSpan(spanBuilder.build());
            sender.sendLog(logBuilder.build());
        } catch (Exception e) {
            System.err.println("[QueueWorker] 배치 처리 실패, 드롭: " + e.getMessage());
        }
    }

    /**
     * 종료 직전 큐에 남은 데이터를 모두 꺼내 마지막으로 전송 시도한다.
     * Integer.MAX_VALUE를 limit으로 넘겨 남은 데이터 전부를 꺼낸다.
     */
    private void flushRemaining() {
        List<QueueItem> remaining = new ArrayList<>();
        queue.drainAll(remaining, Integer.MAX_VALUE);
        if (!remaining.isEmpty()) {
            send(remaining);
        }
    }

    /** 현재 실행 중인지 여부. */
    public boolean isRunning() {
        return running;
    }

    /**
     * 큐의 모든 항목을 즉시 꺼내 전송한다. 테스트에서 주기 대기 없이 전송을 강제할 때
     * 사용한다.
     */
    public void flush() {
        List<QueueItem> batch = new ArrayList<>();
        queue.drainAll(batch, Integer.MAX_VALUE);
        if (!batch.isEmpty()) {
            send(batch);
        }
    }

}