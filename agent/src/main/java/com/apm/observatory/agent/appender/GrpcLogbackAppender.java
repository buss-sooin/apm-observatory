package com.apm.observatory.agent.appender;

import com.apm.common.proto.MonitoringProto;
import com.apm.observatory.agent.AgentContext;

import java.util.Map;

// AppenderBase 상속 제거 이유:
//   GrpcLogbackAppender를 ClassInjector로 targetapp ClassLoader에 주입할 때
//   defineClass 시점에 AppenderBase를 찾으려 하는데
//   AppenderBase는 Spring Boot fat JAR의 BOOT-INF/lib 중첩 JAR 안에 있어서
//   일반 ClassLoader.defineClass()가 중첩 JAR을 탐색하지 못함 → NoClassDefFoundError
//
//   해결: AppenderBase 상속 제거 → 순수 Java 클래스로 변경
//         defineClass 시점에 외부 클래스 참조 없음 → 주입 성공
//         logback과의 연결은 AppenderRegistrationAdvice에서 동적 프록시로 처리
//
//   trade-off: logback 타입(ILoggingEvent 등)을 직접 참조 불가
//              → 리플렉션으로 event 필드 추출
public class GrpcLogbackAppender {

    private volatile boolean started = false;
    private String name = "GrpcLogbackAppender";

    // logback이 Appender.doAppend()를 호출할 때 동적 프록시가 이 메서드로 위임
    // 파라미터: Object event (실제로는 ILoggingEvent이지만 직접 참조 불가)
    public void doAppend(Object event) {
        if (!started) return;
        if (AgentContext.getQueue() == null) return;

        try {
            // ILoggingEvent 필드를 리플렉션으로 추출
            // 이유: ILoggingEvent는 targetapp ClassLoader 소속
            //       GrpcLogbackAppender는 defineClass로 주입되기 전 agent ClassLoader 소속
            //       직접 import하면 defineClass 시점에 타입 불일치 발생
            Class<?> eventClass = event.getClass();

            long timestamp = (long) eventClass.getMethod("getTimeStamp").invoke(event);
            String message = (String) eventClass.getMethod("getFormattedMessage").invoke(event);
            String threadName = (String) eventClass.getMethod("getThreadName").invoke(event);

            // Level 추출
            Object level = eventClass.getMethod("getLevel").invoke(event);
            String levelStr = level.toString();
            int levelInt = (int) level.getClass().getMethod("toInt").invoke(level);

            // MDC에서 trace_id 추출
            @SuppressWarnings("unchecked")
            Map<String, String> mdcMap = (Map<String, String>)
                    eventClass.getMethod("getMDCPropertyMap").invoke(event);
            String traceId = mdcMap != null ? mdcMap.getOrDefault("trace_id", "") : "";

            // StackTrace 추출
            String stackTrace = extractStackTrace(event, eventClass);

            // ERROR_INT 상수값 (logback 기준 40000)
            // 직접 import 불가이므로 상수값 직접 사용
            boolean isError = levelInt >= 40000;

            MonitoringProto.LogData logData = MonitoringProto.LogData.newBuilder()
                    .setAppName(AgentContext.getAppName())
                    .setHost(AgentContext.getHost())
                    .setTimestamp(timestamp)
                    .setLevel(levelStr)
                    .setMessage(message)
                    .setTraceId(traceId)
                    .setThreadName(threadName)
                    .setStackTrace(stackTrace != null ? stackTrace : "")
                    .setError(isError)
                    .build();

            AgentContext.getQueue().offerLog(logData);

        } catch (Exception e) {
            System.err.println("[GrpcLogbackAppender] 로그 적재 실패: " + e.getMessage());
        }
    }

    private String extractStackTrace(Object event, Class<?> eventClass) {
        try {
            Object throwableProxy = eventClass.getMethod("getThrowableProxy").invoke(event);
            if (throwableProxy == null) return null;

            Class<?> proxyClass = throwableProxy.getClass();
            String className = (String) proxyClass.getMethod("getClassName").invoke(throwableProxy);
            String proxyMessage = (String) proxyClass.getMethod("getMessage").invoke(throwableProxy);

            StringBuilder sb = new StringBuilder();
            sb.append(className).append(": ").append(proxyMessage);

            Object[] elements = (Object[]) proxyClass
                    .getMethod("getStackTraceElementProxyArray").invoke(throwableProxy);
            if (elements != null) {
                for (Object element : elements) {
                    String steStr = (String) element.getClass()
                            .getMethod("getSTEAsString").invoke(element);
                    sb.append("\n\tat ").append(steStr);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // logback Appender 인터페이스 메서드들
    // AppenderRegistrationAdvice의 동적 프록시가 이 메서드들로 위임
    public void start() {
        this.started = true;
    }

    public void stop() {
        this.started = false;
    }

    public boolean isStarted() {
        return started;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // logback Context는 사용하지 않지만 Appender 인터페이스 구현을 위해 필요
    public void setContext(Object context) { }

    public Object getCopyOfAttachedFiltersList() {
        return java.util.Collections.emptyList();
    }

    public void clearAllFilters() { }

}