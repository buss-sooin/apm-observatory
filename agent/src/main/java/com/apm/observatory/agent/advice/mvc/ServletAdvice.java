package com.apm.observatory.agent.advice.mvc;

import com.apm.common.proto.MonitoringProto;
import com.apm.observatory.agent.AgentContext;
import com.apm.observatory.agent.queue.DataQueue;
import net.bytebuddy.asm.Advice;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * HTTP 요청 진입점을 후킹해 요청 한 건당 ROOT span을 만든다.
 * onEnter에서 trace_id와 span_id를 새로 만들어 MDC에 넣는다. 같은 요청 스레드에서
 * 실행되는 DB/EXTERNAL advice가 이 span_id를 parentSpanId로 참조해 자식 span이 된다.
 * 이 advice가 만드는 span은 spanType이 ROOT이고 트리 최상단이 된다.
 */
public class ServletAdvice {

    /**
     * 요청 시작 시각을 측정하고 trace_id와 span_id를 새로 만들어 MDC에 넣는다.
     * 여기서 만든 span_id가 자식 span(DB/EXTERNAL)의 parentSpanId 기준점이 된다.
     *
     * @return 요청 시작 시각(ms)
     */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long onEnter() {
        String traceId = UUID.randomUUID().toString();
        String spanId = UUID.randomUUID().toString();
        MDC.put("trace_id", traceId);
        MDC.put("span_id", spanId);
        return System.currentTimeMillis();
    }

    /**
     * 요청 처리 시간을 계산해 ROOT span을 만들어 큐에 넣는다.
     * /health 같은 healthcheck/probe 경로는 추적에서 제외한다. docker healthcheck가
     * 10초마다 호출해 불필요한 span이 쌓이기 때문이다.
     */
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
            @Advice.Enter long startTime,
            @Advice.Argument(0) Object request,
            @Advice.Argument(1) Object response,
            @Advice.Thrown Throwable thrown
    ) {
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        String traceId = MDC.get("trace_id");
        String spanId = MDC.get("span_id");

        try {
            ClassLoader cl = request.getClass().getClassLoader();
            Class<?> httpServletRequestClass = cl.loadClass(
                    "jakarta.servlet.http.HttpServletRequest");
            Class<?> httpServletResponseClass = cl.loadClass(
                    "jakarta.servlet.http.HttpServletResponse");

            String method = (String) httpServletRequestClass
                    .getMethod("getMethod").invoke(request);
            String url = (String) httpServletRequestClass
                    .getMethod("getRequestURI").invoke(request);
            int status = (int) httpServletResponseClass
                    .getMethod("getStatus").invoke(response);

            if ("/health".equals(url)) {
                return;
            }

            MonitoringProto.SpanData span = MonitoringProto.SpanData.newBuilder()
                    .setTraceId(traceId != null ? traceId : "unknown")
                    // onEnter에서 만든 span_id
                    .setSpanId(spanId != null ? spanId : UUID.randomUUID().toString())
                    .setAppName(AgentContext.getAppName() != null ? AgentContext.getAppName() : "")
                    .setHost(AgentContext.getHost() != null ? AgentContext.getHost() : "")
                    .setSpanType("ROOT")
                    .setStartTime(startTime)
                    .setEndTime(endTime)
                    .setDurationMs(duration)
                    .setHttpMethod(method)
                    .setHttpUrl(url)
                    .setHttpStatus(status)
                    .setError(thrown != null)
                    .setErrorMessage(thrown != null && thrown.getMessage() != null
                            ? thrown.getMessage() : "")
                    .build();

            DataQueue queue = AgentContext.getQueue();
            if (queue != null) {
                queue.offerSpan(span);
            }

        } catch (Exception e) {
            System.err.println("[ServletAdvice] Span 수집 실패: " + e.getMessage());
        } finally {
            MDC.remove("trace_id");
            MDC.remove("span_id");
            MDC.remove("external_tracing");
        }
    }

}