package com.apm.observatory.agent.appender;

import com.apm.common.proto.MonitoringProto;
import com.apm.observatory.agent.AgentContext;

import java.util.Map;

/**
 * logback ROOT logger에 붙어 로그 이벤트를 큐로 보내는 appender.
 *
 * <p>일반적인 방식인 logback {@code AppenderBase} 상속을 쓰지 않는다. 이 클래스를
 * {@code ClassInjector}로 logback의 ClassLoader에 주입할 때 {@code defineClass}가
 * {@code AppenderBase}를 찾으려다 Spring Boot fat JAR의 중첩 JAR을 탐색하지 못해
 * {@code NoClassDefFoundError}가 났다. 그래서 상속을 떼고 외부 참조 없는 순수 클래스로
 * 두어 주입이 되게 했고, logback과의 연결은
 * {@link com.apm.observatory.agent.advice.mvc.AppenderRegistrationAdvice}의 동적 프록시가 맡는다.
 *
 * <p>대신 logback 타입({@code ILoggingEvent} 등)을 직접 참조할 수 없어 이벤트 필드는
 * 리플렉션으로 꺼낸다.
 */
public class GrpcLogbackAppender {

    private volatile boolean started = false;
    private String name = "GrpcLogbackAppender";

    /**
     * logback이 로그 이벤트를 넘길 때 동적 프록시가 위임하는 진입점. 이벤트 필드를
     * 리플렉션으로 꺼내 {@code LogData}로 만들어 큐에 넣는다.
     *
     * @param event 실제 타입은 logback {@code ILoggingEvent}지만 직접 참조할 수 없어 Object로 받는다
     */
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

    /** appender를 활성화한다. 이후 들어오는 이벤트만 수집한다. */
    public void start() {
        this.started = true;
    }

    /** appender를 비활성화한다. 이후 이벤트는 무시된다. */
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

    // logback Appender 인터페이스 구색용 no-op
    public void setContext(Object context) { }

    public Object getCopyOfAttachedFiltersList() {
        return java.util.Collections.emptyList();
    }

    public void clearAllFilters() { }

}