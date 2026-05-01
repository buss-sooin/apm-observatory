package com.apm.observatory.agent.advice.mvc;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.description.type.TypeDescription;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;

import com.apm.observatory.agent.appender.GrpcLogbackAppender;
import com.apm.observatory.agent.diagnostic.ClassLoaderDiagnostic;

import java.util.concurrent.atomic.AtomicBoolean;

public class AppenderRegistrationAdvice {

    public static final AtomicBoolean appenderRegistered = new AtomicBoolean(false);

    @Advice.OnMethodExit
    public static void onExit() {
        if (appenderRegistered.compareAndSet(false, true)) {
            registerGrpcAppender();

            // ── ClassLoader 구조 진단 (테스트용) ─────────────────────────
            // initServletBean 완료 시점 — Spring 초기화 후 실제 ClassLoader 구조 확인
            // README 섹션 7 ClassLoader 문제 추적 과정과 연결되는 진단 출력
            // 테스트 완료 후 아래 3개만 남길 것:
            //   printHierarchy() / printAllThreadClassLoaders() / printClassLoaderInfo()
            ClassLoaderDiagnostic.printAllClassLoaders();
            ClassLoaderDiagnostic.printHierarchy();
            ClassLoaderDiagnostic.printAllThreadClassLoaders();
            ClassLoaderDiagnostic.printClassLoaderInfo();
        }
    }

    public static void registerGrpcAppender() {
        try {
            // ── 1. loggerContext를 가진 실제 ClassLoader 탐색 ────────────
            // AppenderBase를 찾는 ClassLoader를 targetCl로 사용하면
            // TomcatEmbeddedWebappClassLoader가 부모 위임으로 AppenderBase를 찾아버려서
            // 실제 logback Context와 다른 ClassLoader에 Appender를 주입하는 문제 발생
            // → LoggerFactory.getILoggerFactory()가 반환하는 loggerContext 객체의
            //   ClassLoader를 직접 가져옴 → 실제 logback을 로드한 ClassLoader 보장
            ClassLoader searchCl = Thread.currentThread().getContextClassLoader();
            ClassLoader targetCl = null;
            Object loggerContext = null;

            while (searchCl != null) {
                try {
                    Class<?> loggerFactoryClass = searchCl.loadClass("org.slf4j.LoggerFactory");
                    loggerContext = loggerFactoryClass.getMethod("getILoggerFactory").invoke(null);
                    targetCl = loggerContext.getClass().getClassLoader();
                    System.out.println("[Agent] logback ClassLoader 발견: "
                            + targetCl.getClass().getName());
                    break;
                } catch (ClassNotFoundException e) {
                    searchCl = searchCl.getParent();
                }
            }

            if (targetCl == null || loggerContext == null) {
                System.err.println("[Agent] logback을 가진 ClassLoader를 찾을 수 없음");
                return;
            }

            // ── 2. ClassInjector로 GrpcLogbackAppender를 targetCl에 주입 ──
            // GrpcLogbackAppender는 AppenderBase를 상속하지 않는 순수 Java 클래스
            // → defineClass 시점에 외부 클래스 참조 없음 → 주입 성공
            // → targetCl 소속이 되므로 targetCl의 logback 타입과 동일 ClassLoader 보장
            Map<TypeDescription, byte[]> types = Collections.singletonMap(
                    new TypeDescription.ForLoadedType(GrpcLogbackAppender.class),
                    ClassFileLocator.ForClassLoader.read(GrpcLogbackAppender.class)
            );

            Map<TypeDescription, Class<?>> injected =
                    new ClassInjector.UsingReflection(targetCl).inject(types);

            final Class<?> appenderImplClass = injected.values().iterator().next();

            // ── 3. GrpcLogbackAppender 인스턴스 생성 ─────────────────────
            final Object appenderImpl = appenderImplClass.getDeclaredConstructor().newInstance();

            // ── 4. logback Appender 인터페이스를 동적 프록시로 구현 ────────
            // GrpcLogbackAppender는 AppenderBase를 상속하지 않으므로
            // logback의 addAppender()가 요구하는 Appender 인터페이스 타입이 아님
            // 동적 프록시로 Appender 인터페이스를 구현하고
            // 각 메서드 호출을 GrpcLogbackAppender 인스턴스로 위임
            // 프록시는 targetCl에서 생성되므로 타입 일치 보장
            Class<?> appenderInterface = targetCl.loadClass("ch.qos.logback.core.Appender");

            Object proxy = Proxy.newProxyInstance(
                    targetCl,
                    new Class<?>[]{ appenderInterface },
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args)
                                throws Throwable {
                            String methodName = method.getName();
                            try {
                                if (args == null || args.length == 0) {
                                    Method m = appenderImplClass.getMethod(methodName);
                                    return m.invoke(appenderImpl);
                                } else {
                                    for (Method m : appenderImplClass.getMethods()) {
                                        if (m.getName().equals(methodName)
                                                && m.getParameterCount() == args.length) {
                                            return m.invoke(appenderImpl, args);
                                        }
                                    }
                                }
                            } catch (NoSuchMethodException e) {
                                // GrpcLogbackAppender에 없는 메서드는 무시
                                // (logback 필터 관련 메서드 등)
                            }
                            return null;
                        }
                    }
            );

            // ── 5. Appender start() 호출 ──────────────────────────────────
            appenderInterface.getMethod("start").invoke(proxy);

            // ── 6. ROOT Logger에 프록시 Appender 등록 ────────────────────
            // proxy는 targetCl에서 생성됐으므로 Appender 타입 일치
            // loggerContext는 1단계에서 이미 획득했으므로 재사용
            Class<?> loggerContextClass = targetCl
                    .loadClass("ch.qos.logback.classic.LoggerContext");
            Object rootLogger = loggerContextClass
                    .getMethod("getLogger", String.class)
                    .invoke(loggerContext, "ROOT");

            rootLogger.getClass()
                    .getMethod("addAppender", appenderInterface)
                    .invoke(rootLogger, proxy);

            System.out.println("[Agent] GrpcLogbackAppender ROOT Logger 등록 성공");

        } catch (Throwable e) {
            System.err.println("[Agent] GrpcLogbackAppender 등록 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

}