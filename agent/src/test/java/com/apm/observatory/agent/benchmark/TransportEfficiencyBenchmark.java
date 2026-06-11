package com.apm.observatory.agent.benchmark;

import com.apm.common.proto.MonitoringProto;
import com.apm.observatory.agent.config.AgentConfig;
import com.apm.observatory.agent.queue.DataQueue;
import com.apm.observatory.agent.queue.DataQueueImpl;
import com.apm.observatory.agent.sender.DataSender;
import com.apm.observatory.agent.sender.GrpcSenderImpl;
import com.apm.observatory.agent.worker.QueueWorker;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * 측정2 — 비동기 stub과 동기 stub의 소비 처리 시간 비교.
 *
 * <p>큐를 {@code ITEM_COUNT}건으로 미리 채운 뒤 실제 {@link QueueWorker}로 비우고, 전건이
 * 응답으로 확정될 때까지의 벽시계 시간을 잰다. 비동기는 프로덕션 {@link GrpcSenderImpl},
 * 동기는 비교군 {@link BlockingGrpcSender}로 같은 큐·worker·서버 구성에서 전송 방식만 바꿔
 * 돌린다. gateway 응답 지연 D를 0부터 키워 가며 같은 측정을 반복한다. D가 0이면 둘이
 * 비슷하고 D가 커질수록 비동기 우위가 커지는 추세 자체가 "비동기가 왕복 지연을 가린다"는
 * 주장의 증거가 된다.
 *
 * <p>공급 스레드를 따로 두지 않고 큐를 미리 채우는 이유는 측정 오염을 피하기 위해서다.
 * 큐를 계속 차 있게 하려고 배경 공급 스레드를 돌리면 그 스레드가 코어를 점유해, 측정 PC의
 * 코어 경쟁이 시간을 왜곡한다. 미리 채워 두면 측정 구간에 공급자가 없어 이 경쟁이 사라진다.
 *
 * <p>이 측정은 한 메서드의 나노초 지연이 아니라 ms~초 규모의 벽시계 시간을 보므로 JMH 같은
 * 마이크로벤치 도구가 필요 없다. 한 번에 한 종류(span)만 채워 배치마다 RPC 한 건이 나가게
 * 한다. 빌드마다 도는 일반 테스트와 분리하려고 {@code benchmark} 태그를 달았고, gradle
 * {@code benchmark} 태스크로만 실행한다.
 */
@Tag("benchmark")
class TransportEfficiencyBenchmark {

    /** 한 arm에서 전송할 span 건수. 큐는 이 수를 담도록 키워 미리 채운다. */
    private static final int ITEM_COUNT = 20_000;

    /** gateway 응답 지연 스윕(ms). 0은 지연이 없을 때 두 stub이 같아지는지 확인하는 대조점이다. */
    private static final long[] DELAY_MILLIS_SWEEP = {0, 1, 5, 20};

    /** 같은 조건 반복 횟수. 가장 깨끗한 값을 보려고 최소값을 표에 싣는다. */
    private static final int REPEAT = 3;

    @Test
    void compareAsyncAndBlockingDrainTime() throws Exception {
        // 첫 실측이 JIT 컴파일 비용을 다 떠안지 않도록 한 번 버린다.
        runArm(Mode.ASYNC, 1, ITEM_COUNT);
        runArm(Mode.BLOCKING, 1, ITEM_COUNT);

        System.out.println();
        System.out.printf("== 측정2: %,d건 전송 소요 시간(ms), 각 조건 %d회 중 최소값 ==%n",
                ITEM_COUNT, REPEAT);
        System.out.printf("%-10s %-12s %-12s %-10s%n", "delayMs", "blocking", "async", "ratio");

        for (long delay : DELAY_MILLIS_SWEEP) {
            long blockingMin = Long.MAX_VALUE;
            long asyncMin = Long.MAX_VALUE;
            for (int i = 0; i < REPEAT; i++) {
                blockingMin = Math.min(blockingMin, runArm(Mode.BLOCKING, delay, ITEM_COUNT));
                asyncMin = Math.min(asyncMin, runArm(Mode.ASYNC, delay, ITEM_COUNT));
            }
            double ratio = asyncMin == 0 ? 0 : (double) blockingMin / asyncMin;
            System.out.printf("%-10d %-12d %-12d %-10.1f%n", delay, blockingMin, asyncMin, ratio);
        }
    }

    private enum Mode { ASYNC, BLOCKING }

    /**
     * 한 조건(전송 방식 + 지연)으로 {@code itemCount}건을 전송하고 전건 응답 확정까지의 시간을
     * ms로 잰다. 지연 서버·채널·sender·worker를 새로 구성해 arm끼리 상태를 공유하지 않는다.
     */
    private long runArm(Mode mode, long delayMillis, int itemCount) throws Exception {
        String serverName = "tx-bench-" + System.nanoTime();
        ExecutorService serverExecutor = Executors.newCachedThreadPool();
        Server server = InProcessServerBuilder.forName(serverName)
                .executor(serverExecutor)
                .addService(new DelayingMonitoringService(delayMillis))
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).build();

        DataSender sender;
        LongSupplier sentCount;
        if (mode == Mode.ASYNC) {
            GrpcSenderImpl asyncSender = new GrpcSenderImpl(channel);
            sender = asyncSender;
            sentCount = asyncSender::getSentCount;
        } else {
            BlockingGrpcSender blockingSender = new BlockingGrpcSender(channel);
            sender = blockingSender;
            sentCount = blockingSender::getSentCount;
        }

        DataQueue queue = new DataQueueImpl(itemCount);
        MonitoringProto.SpanData span = MonitoringProto.SpanData.newBuilder()
                .setSpanId("bench")
                .setSpanType("ROOT")
                .build();
        for (int i = 0; i < itemCount; i++) {
            queue.offerSpan(span);
        }

        int expectedRpcs = (itemCount + AgentConfig.BATCH_SIZE - 1) / AgentConfig.BATCH_SIZE;

        QueueWorker worker = new QueueWorker(queue, sender);
        long startNanos = System.nanoTime();
        worker.start();
        awaitCompletion(sentCount, expectedRpcs);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        worker.stop();
        sender.shutdown();
        server.shutdownNow();
        server.awaitTermination(5, TimeUnit.SECONDS);
        serverExecutor.shutdownNow();
        return elapsedMillis;
    }

    /**
     * 누적 전송 성공 수가 기대 RPC 수에 도달할 때까지 기다린다. 측정이 멈춰 영원히 도는
     * 일을 막으려고 상한 시간을 둔다.
     */
    private void awaitCompletion(LongSupplier sentCount, int expectedRpcs) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
        while (sentCount.getAsLong() < expectedRpcs) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException(
                        "측정 시간 초과 — 보낸 RPC " + sentCount.getAsLong() + "/" + expectedRpcs);
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
