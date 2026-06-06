package com.apm.observatory.agent.collector;

import com.apm.common.proto.MonitoringProto;
import com.apm.observatory.agent.queue.DataQueue;
import com.apm.observatory.agent.config.AgentConfig;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.management.OperatingSystemMXBean;

/**
 * 타깃 JVM과 호스트의 시스템 메트릭을 주기적으로 수집해 {@link DataQueue}에 넣는 수집기.
 *
 * <p>수집 항목은 CPU 사용률, heap 사용량, thread 수, disk 사용량이다. 단일 스레드
 * {@code ScheduledExecutorService}가 {@link AgentConfig#METRICS_INTERVAL_SEC} 주기로
 * {@code collect}를 실행한다. 스레드는 데몬으로 두어 타깃 앱이 종료되면 함께 종료된다.
 *
 * <p>app_name, host, ip, MXBean 참조, FileStore처럼 매번 읽으면 비용이 드는 값은 생성
 * 시점에 한 번만 읽어 필드로 보관하고, 매 수집에서는 보관한 참조만 쓴다.
 *
 * <p>수집 중 예외가 나도 삼켜서 타깃 앱 실행에 영향을 주지 않는다.
 */
public class MetricsCollector {

    private final DataQueue queue;
    private final ScheduledExecutorService executor;

    // 수집 시점마다 읽으면 매번 DNS 조회 비용 발생
    // 생성 시점에 1회만 읽어서 보관
    private final String appName;
    private final String host;
    private final String ip;

    // JVM MXBean — 생성 시점에 1회만 참조 획득
    // ManagementFactory는 JVM 내부 싱글톤이라 매번 호출해도 동일 객체지만
    // 필드로 보관해서 매 수집마다 조회 비용 제거
    private final OperatingSystemMXBean osMXBean;
    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    private final FileStore fileStore;

    public MetricsCollector(DataQueue queue) {
        this.queue = queue;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-collector");
            // 데몬 스레드: 타겟 앱 종료 시 JVM과 함께 종료
            t.setDaemon(true);
            return t;
        });

        // app_name 자동 감지 — 우선순위 순서대로 시도
        // 1순위: JVM 옵션 직접 지정 (-Dapm.app.name=myapp)
        // 2순위: Spring Boot 표준 프로퍼티
        // 3순위: 실행 커맨드에서 메인 클래스명 추출
        // 4순위: 기본값 "unknown"
        this.appName = resolveAppName();

        // host, ip — 생성 시점 1회 읽기
        String resolvedHost = "unknown";
        String resolvedIp = "unknown";
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            resolvedHost = localHost.getHostName();
            resolvedIp = localHost.getHostAddress();
        } catch (UnknownHostException e) {
            System.err.println("[MetricsCollector] host/ip 조회 실패: " + e.getMessage());
        }
        this.host = resolvedHost;
        this.ip = resolvedIp;

        // MXBean 참조 획득
        // com.sun.management.OperatingSystemMXBean은 getCpuLoad() 제공
        // java.lang.management.OperatingSystemMXBean은 CPU 상세 정보 없음
        this.osMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();

        // FileStore — 루트 경로 기준
        FileStore resolvedFileStore = null;
        try {
            resolvedFileStore = Files.getFileStore(Path.of("/"));
        } catch (IOException e) {
            System.err.println("[MetricsCollector] FileStore 조회 실패: " + e.getMessage());
        }
        this.fileStore = resolvedFileStore;
    }

    /** 주기적 수집을 시작한다. 첫 수집은 지연 없이 실행되고 이후 설정 주기로 반복된다. */
    public void start() {
        // initialDelay=0: 즉시 첫 수집 시작
        // period=5: 이후 5초마다 반복
        executor.scheduleAtFixedRate(
                this::collect,
                0,
                AgentConfig.METRICS_INTERVAL_SEC,
                TimeUnit.SECONDS
        );
        System.out.println("[MetricsCollector] 시작 (주기: "
                + AgentConfig.METRICS_INTERVAL_SEC + "초)");
    }

    /** 수집 스케줄러를 종료한다. deadline 안에 끝나지 않으면 강제 종료한다. */
    public void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(
                    AgentConfig.SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        System.out.println("[MetricsCollector] 종료");
    }

    private void collect() {
        try {
            long diskUsed = 0L;
            long diskTotal = 0L;

            if (fileStore != null) {
                diskTotal = fileStore.getTotalSpace();
                diskUsed = diskTotal - fileStore.getUsableSpace();
            }

            // disk_read_bytes, disk_write_bytes
            // Java 표준 API로 IO 누적값을 제공하지 않아 0으로 전송하고,
            // 수집 서버에서 이전값이 없으면 스킵한다
            MonitoringProto.MetricsData metrics = MonitoringProto.MetricsData.newBuilder()
                    .setAppName(appName)
                    .setHost(host)
                    .setIp(ip)
                    .setTimestamp(System.currentTimeMillis())
                    .setCpuUsage(osMXBean.getCpuLoad() * 100)
                    .setHeapUsed(memoryMXBean.getHeapMemoryUsage().getUsed())
                    .setHeapMax(memoryMXBean.getHeapMemoryUsage().getMax())
                    .setThreadCount(threadMXBean.getThreadCount())
                    .setDiskUsed(diskUsed)
                    .setDiskTotal(diskTotal)
                    .setDiskReadBytes(0L)
                    .setDiskWriteBytes(0L)
                    .build();

            queue.offerMetrics(metrics);

        } catch (Exception e) {
            // 수집 실패 시 타겟 앱에 영향 주지 않도록 예외 억제
            // 에이전트 예외 처리 원칙 동일 적용
            System.err.println("[MetricsCollector] 수집 실패: " + e.getMessage());
        }
    }

    // app_name 자동 감지
    // 우선순위 순서대로 시도 후 첫 번째 유효한 값 반환
    private String resolveAppName() {
        // 1순위: JVM 옵션 직접 지정
        String name = System.getProperty(AgentConfig.APP_NAME_PROPERTY);
        if (name != null && !name.isBlank()) return name;

        // 2순위: Spring Boot 표준 프로퍼티
        name = System.getProperty("spring.application.name");
        if (name != null && !name.isBlank()) return name;

        // 3순위: 실행 커맨드에서 앱 이름 추출
        String command = System.getProperty("sun.java.command");
        if (command != null && !command.isBlank()) {
            String mainClass = command.split(" ")[0];

            // JAR 실행인 경우 → 파일명에서 버전 제거
            // "targetappmvc-1.0.0.jar" → "targetappmvc"
            if (mainClass.endsWith(".jar")) {
                String jarName = mainClass.substring(mainClass.lastIndexOf('/') + 1);
                jarName = jarName.replace(".jar", "");
                // 버전 제거: "targetappmvc-1.0.0" → "targetappmvc"
                jarName = jarName.replaceAll("-\\d+.*$", "");
                if (!jarName.isBlank()) return jarName;
            }

            // 일반 클래스 실행인 경우 → 패키지명 제거
            int lastDot = mainClass.lastIndexOf('.');
            if (lastDot >= 0) mainClass = mainClass.substring(lastDot + 1);
            if (!mainClass.isBlank()) return mainClass;
        }

        // 4순위: 기본값
        return AgentConfig.DEFAULT_APP_NAME;
    }

    public String getAppName() {
        return appName;
    }

    public String getHost() {
        return host;
    }

}