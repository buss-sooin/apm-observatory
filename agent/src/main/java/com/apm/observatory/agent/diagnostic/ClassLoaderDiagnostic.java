package com.apm.observatory.agent.diagnostic;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JVM ClassLoader 구조 진단 유틸리티
 *
 * <p>목적:
 * <ul>
 *   <li>JVM에 로드된 전체 ClassLoader 구조 조회 및 출력</li>
 *   <li>특정 클래스 또는 스레드의 ClassLoader 상세 정보 출력</li>
 *   <li>ClassLoader 계층 구조 트리 출력</li>
 * </ul>
 *
 * <p>사용 방식:
 * <ol>
 *   <li>{@code premain()}에서 {@link #init(Instrumentation)} 호출 → Instrumentation 저장</li>
 *   <li>진단이 필요한 시점에 {@code public static} 메서드 직접 호출</li>
 * </ol>
 *
 * <p>설계 원칙:
 * <ul>
 *   <li>인스턴스 생성 불가 — {@code private} 생성자 + {@code UnsupportedOperationException}</li>
 *   <li>모든 메서드 {@code static} — 클래스 이름으로 직접 호출</li>
 *   <li>{@code private static} 조회 메서드를 내부에서 조립하여 {@code public static} 출력 메서드만 노출</li>
 *   <li>진단 도구이므로 핵심 동작({@code AgentContext} 등)에 의존하지 않음</li>
 * </ul>
 */
public final class ClassLoaderDiagnostic {

    private static volatile Instrumentation inst;

    private ClassLoaderDiagnostic() {
        throw new UnsupportedOperationException("유틸리티 클래스는 인스턴스화할 수 없습니다.");
    }

    /**
     * Instrumentation 초기화 — {@code premain()}에서 단 한 번 호출
     *
     * @param instrumentation JVM이 제공하는 Instrumentation 인스턴스
     */
    public static void init(Instrumentation instrumentation) {
        inst = instrumentation;
    }

    // ================================================================
    // private static — 내부 조립용 조회 메서드
    // ================================================================

    /**
     * JVM에 로드된 전체 클래스와 소속 ClassLoader를 Map으로 반환
     * key: 클래스, value: 해당 클래스를 로드한 ClassLoader (null이면 Bootstrap)
     */
    private static Map<Class<?>, ClassLoader> getAllClassLoaders() {
        Map<Class<?>, ClassLoader> map = new HashMap<>();
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            map.put(clazz, clazz.getClassLoader()); // null(Bootstrap) 허용
        }
        return map;
    }

    /**
     * JVM에 로드된 전체 ClassLoader의 부모-자식 관계를 Map으로 반환
     * key: 자식 ClassLoader, value: 부모 ClassLoader (null이면 Bootstrap)
     */
    private static Map<ClassLoader, ClassLoader> getClassLoaderHierarchy() {
        Map<ClassLoader, ClassLoader> map = new HashMap<>();
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            ClassLoader cl = clazz.getClassLoader();
            if (cl != null && !map.containsKey(cl)) {
                map.put(cl, cl.getParent()); // value null(Bootstrap) 허용
            }
        }
        return map;
    }

    /**
     * 전달받은 클래스들이 각각 어느 ClassLoader에 소속되어 있는지 반환
     * key: 클래스, value: 해당 클래스를 로드한 ClassLoader (null이면 Bootstrap)
     *
     * @param classes 조회할 클래스 목록 (varargs)
     */
    private static Map<Class<?>, ClassLoader> getClassLoaderOf(Class<?>... classes) {
        Map<Class<?>, ClassLoader> map = new HashMap<>();
        for (Class<?> clazz : classes) {
            map.put(clazz, clazz.getClassLoader()); // null(Bootstrap) 허용
        }
        return map;
    }

    /**
     * 전달받은 클래스들이 각각 어느 ClassLoader에 소속되어 있는지 반환
     * key: 클래스, value: 해당 클래스를 로드한 ClassLoader (null이면 Bootstrap)
     *
     * @param classes 조회할 클래스 목록 (Collection)
     */
    private static Map<Class<?>, ClassLoader> getClassLoaderOf(Collection<Class<?>> classes) {
        Map<Class<?>, ClassLoader> map = new HashMap<>();
        for (Class<?> clazz : classes) {
            map.put(clazz, clazz.getClassLoader()); // null(Bootstrap) 허용
        }
        return map;
    }

    /**
     * 현재 스레드의 context ClassLoader 반환
     */
    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    /**
     * 특정 스레드의 context ClassLoader 반환
     *
     * @param thread 조회할 스레드
     */
    private static ClassLoader getContextClassLoader(Thread thread) {
        return thread.getContextClassLoader();
    }

    /**
     * JVM 전체 스레드와 각 스레드의 context ClassLoader를 Map으로 반환
     * key: Thread, value: context ClassLoader (null이면 Bootstrap)
     */
    private static Map<Thread, ClassLoader> getAllThreadClassLoaders() {
        Map<Thread, ClassLoader> map = new HashMap<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            map.put(thread, thread.getContextClassLoader()); // null(Bootstrap) 허용
        }
        return map;
    }

    // ================================================================
    // private static — 내부 조립용 헬퍼 메서드
    // ================================================================

    /**
     * {@code printClassLoaderOf()} 오버로딩 공통 출력 로직
     *
     * @param title 출력 타이틀에 삽입할 클래스 이름
     * @param map   클래스-ClassLoader 매핑
     */
    private static void printClassLoaderOfMap(String title, Map<Class<?>, ClassLoader> map) {
        System.out.println("\n===== [Diagnostic] Class[" + title + "] ClassLoader 조회 =====");
        System.out.println();

        map.forEach((clazz, cl) -> {
            System.out.println("Class      : " + clazz.getName());
            System.out.println("ClassLoader: " + cl);
            System.out.println();
        });
    }

    /**
     * ClassLoader 상세 정보 공통 출력 로직
     *
     * @param title 출력 타이틀에 삽입할 대상 객체 설명 (예: "Thread[nio-8080-exec-1]")
     * @param cl    상세 정보를 출력할 ClassLoader
     */
    private static void printClassLoaderInfoDetail(String title, ClassLoader cl) {
        System.out.println("\n===== [Diagnostic] " + title + "의 ClassLoader 상세 정보 =====");
        System.out.println();
        System.out.println("Name               : " + (cl != null
                ? (cl.getName() != null ? cl.getName() : cl.getClass().getSimpleName())
                : "null (Bootstrap)"));
        System.out.println("Parent             : " + (cl != null ? cl.getParent() : "없음"));
        System.out.println("SystemClassLoader  : " + ClassLoader.getSystemClassLoader());
        System.out.println("PlatformClassLoader: " + ClassLoader.getPlatformClassLoader());
        System.out.println();
    }

    /**
     * {@code printHierarchy()}에서 호출 — Bootstrap 직접 자식들을 루트로 재귀 시작
     *
     * @param parentMap 전체 ClassLoader 부모-자식 관계 Map
     */
    private static void buildTreeRecursive(Map<ClassLoader, ClassLoader> parentMap) {
        List<ClassLoader> roots = parentMap.entrySet().stream()
                .filter(e -> e.getValue() == null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        System.out.println("null (Bootstrap)");
        for (int i = 0; i < roots.size(); i++) {
            buildTreeRecursive(parentMap, roots.get(i), 1, i == roots.size() - 1);
        }
    }

    /**
     * DFS 재귀 순회 — depth로 들여쓰기, isLast로 가지 기호 결정
     *
     * @param parentMap 전체 ClassLoader 부모-자식 관계 Map
     * @param current   현재 순회 중인 ClassLoader
     * @param depth     현재 깊이 (들여쓰기 계산용)
     * @param isLast    부모의 마지막 자식 여부 (가지 기호 결정용)
     */
    private static void buildTreeRecursive(Map<ClassLoader, ClassLoader> parentMap,
                                           ClassLoader current, int depth, boolean isLast) {
        String indent = "    ".repeat(depth - 1);
        String branch = isLast ? "└── " : "├── ";
        System.out.println(indent + branch + current);

        List<ClassLoader> children = parentMap.entrySet().stream()
                .filter(e -> current.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (int i = 0; i < children.size(); i++) {
            buildTreeRecursive(parentMap, children.get(i), depth + 1, i == children.size() - 1);
        }
    }

    // ================================================================
    // public static — 외부 노출 출력 메서드
    // ================================================================

    /**
     * JVM에 로드된 전체 클래스 수, ClassLoader 수, ClassLoader 이름 목록 출력
     * 상세 목록이 필요하면 {@link #printAllClassLoadersDetail()} 사용
     */
    public static void printAllClassLoaders() {
        Map<Class<?>, ClassLoader> allMap = getAllClassLoaders();

        Map<Object, List<Class<?>>> grouped = allMap.entrySet().stream()
                .collect(Collectors.groupingBy(
                        e -> e.getValue() == null ? "null (Bootstrap)" : e.getValue(),
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ));

        System.out.println("\n===== [Diagnostic] 전체 ClassLoader 목록 =====");
        System.out.println("총 클래스     : " + allMap.size() + "개");
        System.out.println("총 ClassLoader: " + grouped.size() + "개");
        System.out.println();

        grouped.keySet().forEach(cl -> System.out.println("ClassLoader: " + cl));
        System.out.println();
    }

    /**
     * JVM에 로드된 전체 ClassLoader를 그룹핑하여 소속 클래스 전체 목록 출력
     *
     * <p>출력량이 많으므로 아래 명령어로 필터링 권장:
     * <ul>
     *   <li>그룹 헤더 + 하위 3줄:
     *       {@code docker logs apm-targetapp 2>&1 | grep -A 3 "\[ClassLoader:"}</li>
     *   <li>그룹 헤더만:
     *       {@code docker logs apm-targetapp 2>&1 | grep "\[ClassLoader:"}</li>
     * </ul>
     */
    public static void printAllClassLoadersDetail() {
        Map<Class<?>, ClassLoader> allMap = getAllClassLoaders();

        Map<Object, List<Class<?>>> grouped = allMap.entrySet().stream()
                .collect(Collectors.groupingBy(
                        e -> e.getValue() == null ? "null (Bootstrap)" : e.getValue(),
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ));

        System.out.println("\n===== [Diagnostic] 전체 ClassLoader 상세 목록 =====");
        System.out.println("총 클래스     : " + allMap.size() + "개");
        System.out.println("총 ClassLoader: " + grouped.size() + "개");
        System.out.println();

        grouped.forEach((cl, classes) -> {
            System.out.println("[ClassLoader: " + cl + "] (총 " + classes.size() + "개)");
            classes.forEach(clazz -> System.out.println("  Class: " + clazz.getName()));
            System.out.println();
        });

    }

    /**
     * ClassLoader 계층 구조를 DFS 재귀 트리로 출력
     * Bootstrap(null)을 루트로 자식을 찾아 내려가며 depth로 들여쓰기 관리
     */
    public static void printHierarchy() {
        System.out.println("\n===== [Diagnostic] ClassLoader 계층 구조 =====");
        System.out.println();

        buildTreeRecursive(getClassLoaderHierarchy());
        System.out.println();
    }

    /**
     * 전달받은 클래스들의 소속 ClassLoader 출력 (varargs)
     *
     * @param classes 조회할 클래스 목록
     */
    public static void printClassLoaderOf(Class<?>... classes) {
        String title = Arrays.stream(classes)
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        printClassLoaderOfMap(title, getClassLoaderOf(classes));
    }

    /**
     * 전달받은 클래스들의 소속 ClassLoader 출력 (Collection)
     *
     * @param classes 조회할 클래스 목록
     */
    public static void printClassLoaderOf(Collection<Class<?>> classes) {
        String title = classes.stream()
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        printClassLoaderOfMap(title, getClassLoaderOf(classes));
    }

    /**
     * JVM 전체 스레드의 상태와 context ClassLoader 현황 출력
     */
    public static void printAllThreadClassLoaders() {
        Map<Thread, ClassLoader> map = getAllThreadClassLoaders();

        // ClassLoader 이름 기준으로 그룹핑
        Map<String, List<Thread>> grouped = new HashMap<>();
        map.forEach((thread, cl) -> {
            String clName = cl == null ? "null (Bootstrap)"
                    : (cl.getName() != null ? cl.getName() : cl.getClass().getSimpleName());
            grouped.computeIfAbsent(clName, k -> new java.util.ArrayList<>()).add(thread);
        });

        System.out.println("\n===== [Diagnostic] 전체 스레드 ClassLoader 현황 =====");
        System.out.println();

        // 그룹은 스레드 수 내림차순, 그룹 내 스레드는 이름 알파벳순
        grouped.entrySet().stream()
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .forEach(entry -> {
                    List<Thread> threads = entry.getValue();
                    threads.sort((a, b) -> a.getName().compareTo(b.getName()));

                    System.out.println("[ClassLoader: " + entry.getKey() + "] (총 " + threads.size() + "개)");
                    threads.forEach(t ->
                            System.out.println("  Thread: " + t.getName() + "  [" + t.getState() + "]"));
                    System.out.println();
                });
    }

    /**
     * 현재 스레드의 ClassLoader 상세 정보 출력
     * Name, Parent, SystemClassLoader, PlatformClassLoader 포함
     */
    public static void printClassLoaderInfo() {
        Thread current = Thread.currentThread();
        printClassLoaderInfoDetail("현재 Thread[" + current.getName() + "]", getContextClassLoader());
    }

    /**
     * 특정 스레드의 ClassLoader 상세 정보 출력
     * Name, Parent, SystemClassLoader, PlatformClassLoader 포함
     *
     * @param thread 조회할 스레드
     */
    public static void printClassLoaderInfo(Thread thread) {
        printClassLoaderInfoDetail("Thread[" + thread.getName() + "]", getContextClassLoader(thread));
    }

    /**
     * 특정 클래스의 ClassLoader 상세 정보 출력
     * Name, Parent, SystemClassLoader, PlatformClassLoader 포함
     *
     * @param clazz 조회할 클래스
     */
    public static void printClassLoaderInfo(Class<?> clazz) {
        printClassLoaderInfoDetail("Class[" + clazz.getSimpleName() + "]", clazz.getClassLoader());
    }
}