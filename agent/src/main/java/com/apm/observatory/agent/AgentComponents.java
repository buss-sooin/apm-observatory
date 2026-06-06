package com.apm.observatory.agent;

import com.apm.observatory.agent.collector.MetricsCollector;
import com.apm.observatory.agent.lifecycle.InitializeStep;
import com.apm.observatory.agent.lifecycle.ShutdownStep;
import com.apm.observatory.agent.queue.DataQueue;
import com.apm.observatory.agent.queue.DataQueueImpl;
import com.apm.observatory.agent.sender.DataSender;
import com.apm.observatory.agent.worker.QueueWorker;
import com.apm.observatory.agent.config.AgentConfig;

/**
 * 에이전트 컴포넌트를 조립하고 생명주기를 진행하는 중심 클래스.
 *
 * <p>두 빌더를 조합한다. 내부 {@link Builder}(Nested Builder)는 객체 생성을 맡아 필수값인
 * sender를 검증하고 선택값에 기본값을 채워 {@code build()}로 인스턴스를 만든다. 이어지는
 * {@link InitializeStep}, {@link ShutdownStep}(Step Builder)은 생명주기 실행을 맡아
 * {@code lifecycle() → initialize() → registerShutdownHook()} 순서를 컴파일 타임에 강제한다.
 * 단계를 빠뜨리면 컴파일 에러가 난다.
 *
 * <p>객체 생성({@code build})과 생명주기 시작({@code lifecycle})을 분리해, 만드는 책임과
 * 시작하는 책임을 나눈다.
 */
public class AgentComponents {

    private final DataQueue queue;
    private final DataSender sender;
    private final QueueWorker worker;
    private final MetricsCollector metricsCollector;

    private AgentComponents(Builder builder) {
        this.queue = new DataQueueImpl(AgentConfig.QUEUE_CAPACITY);
        this.sender = builder.sender;
        this.worker = new QueueWorker(this.queue, this.sender);
        this.metricsCollector = new MetricsCollector(this.queue);
    }

    /** Step Builder 진입점. 객체 생성이 끝난 뒤 생명주기를 시작한다. */
    public InitializeStep lifecycle() {
        return new InitializeStep() {
            @Override
            public ShutdownStep initialize() {
                // QueueWorker 백그라운드 스레드 시작
                worker.start();
                metricsCollector.start();

                return new ShutdownStep() {
                    @Override
                    public AgentComponents registerShutdownHook() {
                        // JVM 종료 시 자동으로 destroy() 호출 등록
                        // 타겟 앱 종료(Ctrl+C, System.exit() 등) 시점에 자원 정리
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            System.out.println("[Agent] 종료 시작");
                            destroy();
                            System.out.println("[Agent] 종료 완료");
                        }));
                        return AgentComponents.this;
                    }
                };
            }
        };
    }

    // graceful exit 묶음 — private으로 외부 직접 호출 차단
    // 생명주기(registerShutdownHook) 내부에서만 호출
    // 종료 순서: 수집 중단 → 큐 비우기 → 채널 정리
    private void destroy() {
        metricsCollector.stop();
        worker.stop();
        sender.shutdown();
    }

    public DataQueue getQueue() { return queue; }
    public DataSender getSender() { return sender; }
    public QueueWorker getWorker() { return worker; }

    public static Builder builder() {
        return new Builder();
    }

    /** 큐에 현재 적재된 건수. 0이면 후킹이 안 된 상태라 디버깅 시 먼저 확인하는 값이다. */
    public int getQueueSize() {
        return queue.getQueueSize();
    }

    /** 큐 오버플로우로 드롭된 누적 건수. 0보다 크면 QUEUE_CAPACITY나 BATCH_SIZE 조정이 필요하다. */
    public long getDropCount() {
        return queue.getDropCount();
    }

    public MetricsCollector getMetricsCollector() { return metricsCollector; }

    /** QueueWorker 실행 중 여부. ShutdownHook 중복 호출을 막는 데 쓴다. */
    public boolean isRunning() {
        return worker.isRunning();
    }

    /** 5초 주기를 기다리지 않고 즉시 게이트웨이까지 전송해 전달 여부를 바로 확인한다. */
    public void flush() {
        worker.flush();
    }

    public static class Builder {

        // 필수 — 전송 방식은 외부에서 결정 (전략 패턴), build() 시점에 null 검증
        private DataSender sender;

        // 선택 — 기본값은 AgentConfig 상수
        private String gatewayHost = AgentConfig.GATEWAY_HOST;
        private int gatewayPort = AgentConfig.GATEWAY_PORT;

        public Builder sender(DataSender sender) {
            this.sender = sender;
            return this;
        }

        public Builder gatewayHost(String host) {
            this.gatewayHost = host;
            return this;
        }

        public Builder gatewayPort(int port) {
            this.gatewayPort = port;
            return this;
        }

        /** sender 필수값을 검증하고 {@link AgentComponents}를 생성한다. */
        public AgentComponents build() {
            if (sender == null) {
                throw new IllegalStateException(
                        "[AgentComponents] sender는 필수값입니다. " +
                                "DataSender 구현체를 builder().sender()로 지정하세요."
                );
            }
            return new AgentComponents(this);
        }
    }

}