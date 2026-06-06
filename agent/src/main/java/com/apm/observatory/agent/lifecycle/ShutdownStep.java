package com.apm.observatory.agent.lifecycle;

import com.apm.observatory.agent.AgentComponents;

/**
 * Step Builder 생명주기 2단계. {@code initialize()} 다음에 {@code registerShutdownHook()}을
 * 호출하도록 강제하고, 호출 결과로 완성된 {@link AgentComponents}를 돌려준다.
 */
public interface ShutdownStep {
    AgentComponents registerShutdownHook();
}