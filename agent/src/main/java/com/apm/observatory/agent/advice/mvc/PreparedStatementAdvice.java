package com.apm.observatory.agent.advice.mvc;

import com.apm.common.proto.MonitoringProto.SpanData;
import com.apm.observatory.agent.AgentContext;
import com.apm.observatory.agent.queue.DataQueue;
import net.bytebuddy.asm.Advice;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * JDBC PreparedStatement 실행을 후킹해 DB span을 만든다.
 * ServletAdvice가 MDC에 넣은 trace_id/span_id를 읽어 이 DB span을 ROOT span의
 * 자식으로 연결한다. trace_id가 없으면 ServletAdvice 후킹 범위 밖이라 수집하지 않는다.
 * suppress = Throwable.class로 advice 자체 결함이 타깃 앱으로 전파돼 앱을 죽이는 일을 막는다.
 */
public class PreparedStatementAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long onEnter(@Advice.This Object stmt) {
        return System.currentTimeMillis();
    }

    /**
     * 실행 시간을 계산해 DB span을 만들어 큐에 넣는다.
     * onThrowable = Throwable.class라 타깃 메서드가 예외를 던져도 onExit이 실행돼,
     * 실패한 요청의 DB span도 누락 없이 수집한다.
     */
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
            @Advice.Enter long startTime,
            @Advice.This Object preparedStatement,
            @Advice.Thrown Throwable thrown) {

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        String traceId = MDC.get("trace_id");

        if (traceId == null) return;

        try {
            // PreparedStatement.toString()이 실행 SQL을 반환하는 드라이버가 많다(MySQL, PostgreSQL 등).
            // 드라이버마다 구현이 달라 정확히 하려면 드라이버별 처리가 필요하다.
            String sql = preparedStatement.toString();

            SpanData span = SpanData.newBuilder()
                    .setTraceId(traceId)
                    .setSpanId(UUID.randomUUID().toString())
                    // parentSpanId에 ROOT span의 span_id를 넣어 DB span을 그 자식으로 연결
                    .setParentSpanId(MDC.get("span_id") != null ? MDC.get("span_id") : "")
                    .setAppName(AgentContext.getAppName())
                    .setHost(AgentContext.getHost())
                    .setSpanType("DB")
                    .setStartTime(startTime)
                    .setEndTime(endTime)
                    .setDurationMs(duration)
                    .setSqlQuery(sql)
                    .setError(thrown != null)
                    .setErrorMessage(thrown != null ? thrown.getMessage() : "")
                    .build();

            DataQueue queue = AgentContext.getQueue();
            if (queue != null) {
                queue.offerSpan(span);
            }

        } catch (Exception e) {
            System.err.println("[PreparedStatementAdvice] Span 수집 실패: "
                    + e.getMessage());
        }
    }

}