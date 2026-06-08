package com.apm.observatory.apiserver.metrics.entity;

import jakarta.persistence.*;

/**
 * metrics 테이블 읽기 전용 매핑. collectorserver가 JdbcTemplate으로 저장하고 apiserver는
 * JPA로 조회만 한다.
 */
@Entity
@Table(name = "metrics")
public class MetricsEntity {

    @EmbeddedId
    private MetricsPk id;

    @Column(name = "host")
    private String host;

    @Column(name = "ip")
    private String ip;

    @Column(name = "cpu_usage")
    private Double cpuUsage;

    @Column(name = "heap_used")
    private Long heapUsed;

    @Column(name = "heap_max")
    private Long heapMax;

    @Column(name = "thread_count")
    private Integer threadCount;

    @Column(name = "disk_used")
    private Long diskUsed;

    @Column(name = "disk_total")
    private Long diskTotal;

    @Column(name = "disk_read_bytes")
    private Long diskReadBytes;

    @Column(name = "disk_write_bytes")
    private Long diskWriteBytes;

    protected MetricsEntity() {}

    public MetricsPk getId() { return id; }
    public String getHost() { return host; }
    public String getIp() { return ip; }
    public Double getCpuUsage() { return cpuUsage; }
    public Long getHeapUsed() { return heapUsed; }
    public Long getHeapMax() { return heapMax; }
    public Integer getThreadCount() { return threadCount; }
    public Long getDiskUsed() { return diskUsed; }
    public Long getDiskTotal() { return diskTotal; }
    public Long getDiskReadBytes() { return diskReadBytes; }
    public Long getDiskWriteBytes() { return diskWriteBytes; }

}