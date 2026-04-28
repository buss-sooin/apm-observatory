package com.apm.observatory.agent;

import com.apm.observatory.agent.collector.MetricsCollector;
import com.apm.observatory.agent.lifecycle.InitializeStep;
import com.apm.observatory.agent.lifecycle.ShutdownStep;
import com.apm.observatory.agent.queue.DataQueue;
import com.apm.observatory.agent.queue.DataQueueImpl;
import com.apm.observatory.agent.sender.DataSender;
import com.apm.observatory.agent.worker.QueueWorker;
import com.apm.observatory.agent.config.AgentConfig;

// Nested Builder + Step Builder 조합
//
// Nested Builder (Builder 내부 클래스):
//   객체 생성 담당 — 무엇을 만드나
//   필수값(sender) 검증, 선택값(host/port) 기본값 제공
//   build() → AgentComponents 반환
//
// Step Builder (InitializeStep, ShutdownStep 인터페이스):
//   생명주기 실행 담당 — 어떤 순서로 시작하나
//   build() 이후 lifecycle() 진입 → initialize() → registerShutdownHook() 순서 강제
//   각 단계 누락 시 컴파일 에러
//
// 규모가 커진다면 여기서 더 다듬어야 할 것들이 있음
//   생명주기 단계가 validate() → authenticate() → initialize()
//   → warmup() → registerShutdownHook()으로 확장됨
//   추후 람다 기반으로 각 단계를 함수형 인터페이스로 주입받는 구조로 리팩토링 예정
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

    // Step Builder 진입점
    // build() 로 객체 생성 완료 후 lifecycle()로 생명주기 시작
    // 생성(build)과 실행(lifecycle) 책임 분리
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
    // 규모가 커진다면 여기서 더 다듬어야 할 것들이 있음 서버에 에이전트 종료 이벤트 전송 추가
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

    // 큐에 현재 적재된 건수
    // 0이면 후킹이 안 된 것, 디버깅 시 첫 번째 확인 포인트
    public int getQueueSize() {
        return queue.getQueueSize();
    }

    // 큐 오버플로우로 드롭된 누적 건수
    // 0보다 크면 QUEUE_CAPACITY 또는 BATCH_SIZE 조정 필요
    public long getDropCount() {
        return queue.getDropCount();
    }

    public MetricsCollector getMetricsCollector() { return metricsCollector; }

    // QueueWorker 실행 중 여부
    // ShutdownHook 중복 호출 방지에 활용
    public boolean isRunning() {
        return worker.isRunning();
    }

    // 즉시 강제 전송
    // 5초 주기 기다리지 않고 게이트웨이까지 전달 여부 바로 확인 가능
    public void flush() {
        worker.flush();
    }

    public static class Builder {

        // 필수 — 전송 방식은 외부에서 결정 (전략 패턴)
        // build() 시점에 null 검증
        private DataSender sender;

        // 선택 — 기본값은 AgentConfig 상수
        // agentArgs나 설정 파일로 동적 변경 가능하도록 확장 포인트 제공
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

        // build()는 AgentComponents 반환 — 객체 생성 완료
        // 생명주기 시작은 lifecycle()로 분리
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