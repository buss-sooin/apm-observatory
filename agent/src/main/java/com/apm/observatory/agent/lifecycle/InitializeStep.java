package com.apm.observatory.agent.lifecycle;

/**
 * Step Builder 생명주기 1단계. {@code build()} 다음에 {@code initialize()}를 먼저
 * 호출하도록 컴파일 타임에 강제하고, 호출 결과로 다음 단계인 {@link ShutdownStep}을 넘긴다.
 */
public interface InitializeStep {
    ShutdownStep initialize();
}