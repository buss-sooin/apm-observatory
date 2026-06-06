package com.apm.observatory.agent;

import com.apm.observatory.agent.queue.DataQueue;

/**
 * Advice가 {@link DataQueue}에 접근하도록 참조를 보관하는 static 홀더.
 *
 * <p>Byte Buddy Advice는 static 메서드만 허용해 인스턴스 필드를 둘 수 없다. 그래서 큐
 * 참조를 static 필드로 전역 보관하고 Advice가 static 메서드로 꺼내 쓴다.
 *
 * <p>필드를 {@code volatile}로 둔다. {@code init}은 초기화 스레드(AgentMain)가 호출하고
 * {@code getQueue}는 이후 요청 스레드들이 호출하므로, 스레드 간 가시성을 보장해야 한다.
 */
public class AgentContext {

    private static volatile DataQueue queue;
    private static volatile String appName;
    private static volatile String host;

    /**
     * 컴포넌트 초기화가 끝난 뒤 AgentMain이 한 번 호출한다. appName, host는
     * MetricsCollector가 수집한 값을 재사용한다.
     */
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