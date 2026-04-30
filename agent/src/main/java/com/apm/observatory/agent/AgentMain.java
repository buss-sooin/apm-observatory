package com.apm.observatory.agent;

import com.apm.observatory.agent.advice.mvc.AppenderRegistrationAdvice;
import com.apm.observatory.agent.diagnostic.ClassLoaderDiagnostic;
import com.apm.observatory.agent.advice.mvc.PreparedStatementAdvice;
import com.apm.observatory.agent.advice.mvc.RestClientRequestAdvice;
import com.apm.observatory.agent.sender.GrpcSenderImpl;
import com.apm.observatory.agent.advice.mvc.ServletAdvice;
import com.apm.observatory.agent.config.AgentConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class AgentMain {

    // [Composition Root]
    // premain()은 JVM 시작 시 단 한 번 실행되는 진입점
    // Spring 없는 순수 Java 환경에서
    // 모든 컴포넌트 생성 및 조립 책임을 여기서 담당

    // [APM 에이전트 예외 처리 원칙]
    // 에이전트는 절대 타겟 앱에 영향을 주면 안 된다.
    // premain()에서 예외가 새어나가면 JVM이 타겟 앱 실행을 중단시킨다.
    // 따라서 모든 후킹은 독립적인 try-catch로 격리하고
    // 실패 시 에이전트 로그만 남기고 타겟 앱은 정상 실행을 보장한다.
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[Agent] 시작");

        // Instrumentation 저장 — ClassLoaderDiagnostic에서 사용
        ClassLoaderDiagnostic.init(inst);

        // ===== 컴포넌트 조립 =====

        // ManagedChannel: gRPC 게이트웨이 연결
        // 채널 생성 책임은 Composition Root인 AgentMain이 보유
        // GrpcSenderImpl은 전송 책임만 보유 (단일 책임 원칙)
        // 보안이 필요하다면 암호화 통신을 붙여야 하고, 트래픽이 늘면 연결 관리 방식도 달라져야 할 것 같음
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(AgentConfig.GATEWAY_HOST, AgentConfig.GATEWAY_PORT)
                .usePlaintext()
                .build();

        // Nested Builder로 컴포넌트 조립 후 Step Builder로 생명주기 순서 강제
        // sender:           필수 — 전송 방식 결정 (전략 패턴, 교체 시 이 라인만 변경)
        // gatewayHost/Port: 선택 — 기본값 AgentConfig 사용, 동적 변경 시 명시
        // queue, worker:    Nested Builder 내부에서 자동 초기화
        // build():          AgentComponents 생성 완료 (객체 생성과 생명주기 분리)
        // lifecycle():      Step Builder 진입 — InitializeStep 반환
        // initialize():     QueueWorker 시작 — ShutdownStep 반환
        // registerShutdownHook(): JVM 종료 시 destroy() 자동 등록 — AgentComponents 반환
        AgentComponents components = AgentComponents.builder()
                .sender(new GrpcSenderImpl(channel))
                .build()
                .lifecycle()
                .initialize()
                .registerShutdownHook();

        // AgentContext 초기화 — Advice들이 DataQueue에 접근할 수 있도록
        // 후킹 설치 전에 반드시 초기화해야 함
        // 후킹 설치 후 요청이 들어오면 즉시 Advice가 실행되므로
        // DataQueue가 null이면 NPE → 타겟 앱 영향
        AgentContext.init(
                components.getQueue(),
                components.getMetricsCollector().getAppName(),
                components.getMetricsCollector().getHost()
        );

        // ===== MVC 환경 후킹 =====

        // DispatcherServlet.init() 후킹
        // Spring 초기화 완료 시점에 GrpcLogbackAppender 1회 등록
        try {
            new AgentBuilder.Default()
                    .with(AgentBuilder.Listener.NoOp.INSTANCE)
                    // FrameworkServlet이 init()을 실제로 오버라이드한 클래스이므로 후킹 대상 변경
                    .type(named("org.springframework.web.servlet.FrameworkServlet"))
                    .transform((builder, typeDescription, classLoader, module, domain) ->
                            builder.visit(Advice.to(AppenderRegistrationAdvice.class)
                                    .on(named("initServletBean").and(takesArguments(0))))
                    )
                    .installOn(inst);
            System.out.println("[Agent] DispatcherServlet.init() 후킹 성공");
        } catch (Exception e) {
            System.err.println("[Agent] DispatcherServlet.init() 후킹 실패: " + e.getMessage());
        }

        // DispatcherServlet 후킹
        // TraceID 생성 + MDC 전파 + 전체 응답시간 측정
        try {
            new AgentBuilder.Default()
                    .with(new AgentBuilder.Listener.Adapter() {
                        @Override
                        public void onError(String typeName, ClassLoader classLoader,
                                            JavaModule module, boolean loaded, Throwable throwable) {
                            System.err.println("[Agent] 변환 실패: " + typeName + " / " + throwable);
                        }
                    })
                    .type(named("org.springframework.web.servlet.DispatcherServlet"))
                    .transform((builder, typeDescription, classLoader, module, domain) ->
                            builder.visit(Advice.to(ServletAdvice.class)
                                    .on(named("doDispatch")))
                    )
                    .installOn(inst);
            System.out.println("[Agent] DispatcherServlet 후킹 성공");
        } catch (Exception e) {
            System.err.println("[Agent] DispatcherServlet 후킹 실패: " + e.getMessage());
        }

        // PreparedStatement 후킹
        // DB Span 수집 (프록시 계층 제외)
        try {
            new AgentBuilder.Default()
                    .with(AgentBuilder.Listener.NoOp.INSTANCE)
                    .type(isSubTypeOf(Class.forName("java.sql.PreparedStatement"))
                            .and(not(nameContains("Proxy")))
                            .and(not(nameContains("Hikari"))))
                    .transform((builder, typeDescription, classLoader, module, domain) ->
                            builder.method(named("execute")
                                            .or(named("executeQuery"))
                                            .or(named("executeUpdate")))
                                    .intercept(Advice.to(PreparedStatementAdvice.class))
                    )
                    .installOn(inst);
            System.out.println("[Agent] PreparedStatement 후킹 성공");
        } catch (ClassNotFoundException e) {
            System.err.println("[Agent] PreparedStatement 클래스 없음: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[Agent] PreparedStatement 후킹 실패: " + e.getMessage());
        }

        // RestClient 외부 호출 후킹
        // MVC 동기 방식 EXTERNAL Span 수집
        // exchangeInternal() private 메서드라 visit 방식 사용
        try {
            new AgentBuilder.Default()
                    .with(AgentBuilder.Listener.NoOp.INSTANCE)
                    .type(named("org.springframework.web.client.DefaultRestClient$DefaultRequestBodyUriSpec"))
                    .transform((builder, typeDescription, classLoader, module, domain) ->
                            builder.visit(Advice.to(RestClientRequestAdvice.class)
                                    .on(named("exchangeInternal")))
                    )
                    .installOn(inst);
            System.out.println("[Agent] RestClient 후킹 성공");
        } catch (Exception e) {
            System.err.println("[Agent] RestClient 후킹 실패: " + e.getMessage());
        }
    }

}