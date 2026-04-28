package com.apm.observatory.agent.lifecycle;

// Step Builder 생명주기 1단계 인터페이스
// build() 이후 반드시 initialize()를 먼저 호출하도록 컴파일 타임 강제
// initialize() 없이 registerShutdownHook() 접근 불가
// 단계를 더 세분화한다면 검증 → 인증 → 초기화 순서가 자연스러울 것 같음
public interface InitializeStep {
    ShutdownStep initialize();
}