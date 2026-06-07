package com.apm.observatory.collectorserver.consumer;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 등록된 모든 {@link AbstractStreamConsumer}를 모아 두는 보관소. {@link StreamConsumerScheduler}가
 * 구체 Consumer 타입을 알지 않고 전체에 주기 호출을 전달하도록 그 사이에 둔다.
 *
 * <p>Spring은 생성자의 {@code List<AbstractStreamConsumer>} 파라미터에 해당 타입의 빈을
 * 모두 주입한다. 현재 주입되는 구현체는 MetricsConsumer, SpanConsumer, LogConsumer다.
 */
@Component
public class StreamConsumerProvider {

    private final List<AbstractStreamConsumer> consumers;

    public StreamConsumerProvider(List<AbstractStreamConsumer> consumers) {
        this.consumers = consumers;
    }

    public List<AbstractStreamConsumer> getAll() {
        return consumers;
    }

}
