package com.apm.observatory.agent.lifecycle;

import com.apm.observatory.agent.AgentComponents;

// Step Builder 생명주기 2단계 인터페이스
// initialize() 이후 반드시 registerShutdownHook()을 호출하도록 컴파일 타임 강제
// registerShutdownHook() 없이 AgentComponents 반환 불가
// 초기화 단계가 늘어난다면 준비 단계가 앞에 더 붙는 구조가 될 것 같음
public interface ShutdownStep {
    AgentComponents registerShutdownHook();
}