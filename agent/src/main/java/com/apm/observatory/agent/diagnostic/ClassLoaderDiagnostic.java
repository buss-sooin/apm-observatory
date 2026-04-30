package com.apm.observatory.agent.diagnostic;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JVM ClassLoader 구조 진단 유틸리티
 *
 * 목적:
 *   - JVM에 로드된 ClassLoader 전체 목록 조회
 *   - 특정 클래스들의 ClassLoader 소속 확인
 *   - ClassLoader 계층 구조 (부모 체인) 출력
 *
 * 사용 방식:
 *   1. premain()에서 ClassLoaderDiagnostic.init(inst) 호출 → Instrumentation 저장
 *   2. initServletBean 완료 시점에 ClassLoaderDiagnostic.run() 호출 → 진단 출력
 *
 * 설계 원칙:
 *   - 순수 기능 단위 static 메서드로 구성
 *   - 조회 메서드들의 조합으로 다양한 진단 가능
 *   - 진단 도구이므로 핵심 동작(AgentContext 등)에 의존하지 않음
 */
public class ClassLoaderDiagnostic {

    private static volatile Instrumentation inst;

    // premain() 에서 호출 — Instrumentation 저장
    public static void init(Instrumentation instrumentation) {
        inst = instrumentation;
    }

    // initServletBean 완료 시점에 호출 — 전체 진단 실행
    public static void run() {
        if (inst == null) {
            System.err.println("[Diagnostic] Instrumentation 미초기화");
            return;
        }

        printAllClassLoaders();
        // 이후 단계별로 메서드 추가 예정
    }

    /**
     * JVM에 로드된 모든 ClassLoader 목록 (중복 제거, flat list)
     * Instrumentation.getAllLoadedClasses()로 전체 클래스를 가져와
     * 각 클래스의 getClassLoader()를 중복 제거해서 반환
     */
    public static Set<ClassLoader> getAllClassLoaders() {
        return Arrays.stream(inst.getAllLoadedClasses())
                .map(Class::getClassLoader)
                .collect(Collectors.toSet());
    }

    /**
     * 전체 ClassLoader 목록과 각 ClassLoader의 부모를 출력
     * 실제 출력 결과를 보고 계층 구조 설계 방향 결정
     */
    private static void printAllClassLoaders() {
        System.out.println("\n===== [Diagnostic] 로드된 ClassLoader 전체 목록 =====");

        Set<ClassLoader> classLoaders = getAllClassLoaders();
        System.out.println("총 " + classLoaders.size() + "개");
        System.out.println();

        classLoaders.forEach(cl -> {
            System.out.println("CL  : " + cl);
            System.out.println("부모: " + (cl != null ? cl.getParent() : "없음 (최상위)"));
            System.out.println();
        });

        System.out.println("=====================================================\n");
    }
}