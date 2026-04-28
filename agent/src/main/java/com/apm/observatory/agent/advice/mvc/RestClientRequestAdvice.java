package com.apm.observatory.agent.advice.mvc;

import com.apm.common.proto.MonitoringProto.SpanData;
import com.apm.observatory.agent.AgentContext;
import net.bytebuddy.asm.Advice;
import com.apm.observatory.agent.queue.DataQueue;
import org.slf4j.MDC;

import java.util.UUID;

public class RestClientRequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long onEnter() {
        if (MDC.get("external_tracing") != null) return -1L;
        MDC.put("external_tracing", "active");
        return System.currentTimeMillis();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
            @Advice.Enter long startTime,
            @Advice.Thrown Throwable thrown
    ) {
        if (startTime == -1L) return;

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        String traceId = MDC.get("trace_id");

        try {
            SpanData span = SpanData.newBuilder()
                    .setTraceId(traceId != null ? traceId : "unknown")
                    .setSpanId(UUID.randomUUID().toString())
                    // 의도: parentSpanId를 traceId 대신 span_id로 변경
                    // span_id = ServletAdvice가 생성한 INTERNAL Span의 spanId
                    // → EXTERNAL Span이 INTERNAL Span의 자식으로 트리 구조 성립
                    .setParentSpanId(MDC.get("span_id") != null ? MDC.get("span_id") : "")
                    .setAppName(AgentContext.getAppName())
                    .setHost(AgentContext.getHost())
                    .setSpanType("EXTERNAL")
                    .setStartTime(startTime)
                    .setEndTime(endTime)
                    .setDurationMs(duration)
                    .setError(thrown != null)
                    .setErrorMessage(thrown != null ? thrown.getMessage() : "")
                    .build();

            DataQueue queue = AgentContext.getQueue();
            if (queue != null) {
                queue.offerSpan(span);
            }
        } catch (Exception e) {
            System.err.println("[RestClientRequestAdvice] Span 수집 실패: "
                    + e.getMessage());
        } finally {
            MDC.remove("external_tracing");
        }
    }

}