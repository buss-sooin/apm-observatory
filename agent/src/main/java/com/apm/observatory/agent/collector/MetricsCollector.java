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
        // 마운트 포인트가 여러 개라면 전부 순회해서 합산해야 더 정확할 것 같음
        FileStore resolvedFileStore = null;
        try {
            resolvedFileStore = Files.getFileStore(Path.of("/"));
        } catch (IOException e) {
            System.err.println("[MetricsCollector] FileStore 조회 실패: " + e.getMessage());
        }
        this.fileStore = resolvedFileStore;
    }

    public void start() {
        // initialDelay=0: 즉시 첫 수집 시작
        // period=5: 이후 5초마다 반복
        // 이상이 감지됐을 때 수집 주기를 줄이면 더 세밀하게 볼 수 있을 것 같음
        executor.scheduleAtFixedRate(
                this::collect,
                0,
                AgentConfig.METRICS_INTERVAL_SEC,
                TimeUnit.SECONDS
        );
        System.out.println("[MetricsCollector] 시작 (주기: "
                + AgentConfig.METRICS_INTERVAL_SEC + "초)");
    }

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
            // Java 표준 API로 IO 누적값 제공 안 함
            // Disk IO 누적값은 표준 API로는 못 가져와서, 정확히 하려면 OS 레벨로 내려가야 할 것 같음
            // 지금은 0으로 전송하고 수집 서버에서 이전값 없으면 스킵하는 방식으로 처리함
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