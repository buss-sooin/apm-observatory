package com.apm.observatory.agent.advice.mvc;

import com.apm.common.proto.MonitoringProto;
import com.apm.observatory.agent.AgentContext;
import com.apm.observatory.agent.queue.DataQueue;
import net.bytebuddy.asm.Advice;
import org.slf4j.MDC;

import java.util.UUID;

public class ServletAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long onEnter() {
        String traceId = UUID.randomUUID().toString();
        // 의도: spanId를 MDC에 저장
        // DB/EXTERNAL Span의 parentSpanId가 이 값을 참조해서 트리 구조 성립
        // traceId와 spanId를 분리 → INTERNAL이 root, DB/EXTERNAL이 자식으로 연결
        String spanId = UUID.randomUUID().toString();
        MDC.put("trace_id", traceId);
        MDC.put("span_id", spanId);
        return System.currentTimeMillis();
    }

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

            // 의도: healthcheck/probe 경로는 tracing 제외
            // docker healthcheck가 10초마다 호출하므로 span이 불필요하게 쌓임
            if ("/health".equals(url)) {
                return;
            }

            MonitoringProto.SpanData span = MonitoringProto.SpanData.newBuilder()
                    .setTraceId(traceId != null ? traceId : "unknown")
                    // 의도: onEnter에서 생성한 spanId 사용 → DB/EXTERNAL의 parentSpanId 기준점
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
            MDC.remove("span_id");      // 추가
            MDC.remove("external_tracing");
        }
    }

}