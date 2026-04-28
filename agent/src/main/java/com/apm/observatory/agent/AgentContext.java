package com.apm.observatory.agent;

import com.apm.observatory.agent.queue.DataQueue;

// Advice에서 DataQueue에 접근하기 위한 static 홀더
// 이유:
//   Byte Buddy Advice는 static 메서드만 허용 → 인스턴스 필드 불가
//   static 홀더로 DataQueue 참조를 전역 보관
//
// volatile 이유:
//   AgentMain(초기화 스레드)이 init() 호출
//   이후 요청 스레드들이 getQueue() 호출
//   서로 다른 스레드 간 가시성 보장 필요
//
// 더 나아간다면 여기서 고려해야 할 것들이 있음
//   AgentContext가 DataQueue 외에 설정값, 샘플링 정책 등도 보관
//   에이전트 전체의 런타임 컨텍스트 역할
public class AgentContext {

    private static volatile DataQueue queue;
    private static volatile String appName;
    private static volatile String host;

    // AgentMain에서 컴포넌트 초기화 후 1회 호출
    // appName, host — MetricsCollector에서 이미 수집한 값 재사용
    public static void init(DataQueue queue, String appName, String host) {
        AgentContext.queue = queue;
        AgentContext.appName = appName;
        AgentContext.host = host;
    }

    public static DataQueue getQueue() {
        return queue;
    }

    public static String getAppName() {
        return appName != null ? appName : "unknown";
    }

    public static String getHost() {
        return host != null ? host : "unknown";
    }

}