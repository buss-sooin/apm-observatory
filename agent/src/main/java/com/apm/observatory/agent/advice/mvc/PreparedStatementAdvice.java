package com.apm.observatory.agent.advice.mvc;

import com.apm.common.proto.MonitoringProto.SpanData;
import com.apm.observatory.agent.AgentContext;
import com.apm.observatory.agent.queue.DataQueue;
import net.bytebuddy.asm.Advice;
import org.slf4j.MDC;

import java.util.UUID;

public class PreparedStatementAdvice {

    // suppress = Throwable.class
    // Advice 코드 자체에서 예외 발생 시 타겟 앱에 전파되지 않도록 억제
    // APM 에이전트 원칙: 에이전트 버그가 타겟 앱 크래시를 유발하면 안 됨
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long onEnter(@Advice.This Object stmt) {
        return System.currentTimeMillis();
    }

    // suppress = Throwable.class → Advice 코드 예외 억제
    // onThrowable = Throwable.class → 타겟 메서드 예외 발생 시에도 onExit 실행 보장
    // 예외 발생 요청의 DB Span도 누락 없이 수집
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
            @Advice.Enter long startTime,
            @Advice.This Object preparedStatement,
            @Advice.Thrown Throwable thrown) {

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        String traceId = MDC.get("trace_id");

        // traceId 없으면 ServletAdvice 후킹 범위 밖 → 수집 불필요
        if (traceId == null) return;

        try {
            // PreparedStatement에서 SQL 추출
            // toString()이 SQL을 반환하는 드라이버가 많음 (MySQL, PostgreSQL 등)
            // 드라이버마다 내부 구현이 달라서, 정확하게 하려면 드라이버별로 다른 처리가 필요할 것 같음
            String sql = preparedStatement.toString();

            SpanData span = SpanData.newBuilder()
                    .setTraceId(traceId)
                    .setSpanId(UUID.randomUUID().toString())
                    // 의도: parentSpanId를 traceId 대신 span_id로 변경
                    // span_id = ServletAdvice가 생성한 INTERNAL Span의 spanId
                    // → DB Span이 INTERNAL Span의 자식으로 트리 구조 성립
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