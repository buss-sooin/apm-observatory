package com.apm.observatory.agent.advice.mvc;

import com.apm.common.proto.MonitoringProto.SpanData;
import com.apm.observatory.agent.AgentContext;
import net.bytebuddy.asm.Advice;
import com.apm.observatory.agent.queue.DataQueue;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * 외부 HTTP 호출을 후킹해 EXTERNAL span을 만든다.
 * MDC의 external_tracing 플래그로 한 외부 호출이 중첩 후킹돼 EXTERNAL span이
 * 두 번 잡히는 것을 막는다. ROOT span의 span_id를 parentSpanId로 참조해 자식으로 연결한다.
 */
public class RestClientRequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long onEnter() {
        // 이미 외부 호출을 추적 중이면 같은 호출의 중첩 후킹이라 건너뛴다(-1L 반환)
        if (MDC.get("external_tracing") != null) return -1L;
        MDC.put("external_tracing", "active");
        return System.currentTimeMillis();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
            @Advice.Enter long startTime,
            @Advice.Thrown Throwable thrown
    ) {
        // onEnter에서 건너뛴 중첩 호출(-1L)이면 span을 만들지 않는다
        if (startTime == -1L) return;

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        String traceId = MDC.get("trace_id");

        try {
            SpanData span = SpanData.newBuilder()
                    .setTraceId(traceId != null ? traceId : "unknown")
                    .setSpanId(UUID.randomUUID().toString())
                    // parentSpanId에 ROOT span의 span_id를 넣어 EXTERNAL span을 그 자식으로 연결
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