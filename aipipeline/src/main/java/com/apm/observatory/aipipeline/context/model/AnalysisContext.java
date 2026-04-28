package com.apm.observatory.aipipeline.context.model;

import com.apm.observatory.aipipeline.performance.model.PerformanceSnapshot;
import com.apm.observatory.aipipeline.performance.model.PerformanceTrend;
import com.apm.observatory.aipipeline.performance.port.ExternalImpactDataPort.ExternalSpanSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.MetricsSnapshot;
import com.apm.observatory.aipipeline.performance.port.PerformanceDataPort.SpanSnapshot;
import com.apm.observatory.aipipeline.threshold.model.ThresholdConfig;

import java.util.List;

public record AnalysisContext(
        String appName,
        PerformanceTrend trend,
        double spikeMultiplier,
        double cpuThreshold,
        double memoryThreshold,
        double externalRatioMultiplier,
        double baselineCpuAvg,
        double baselineHeapAvg,
        double baselineSpanAvg,
        double baselineExternalAvg,
        List<MetricsSnapshot> recentMetrics,
        List<SpanSnapshot> recentSpans,
        List<ExternalSpanSnapshot> recentExternalSpans
) {
    public AnalysisContext withSnapshot(PerformanceSnapshot snapshot) {
        return new AnalysisContext(
                this.appName, this.trend,
                this.spikeMultiplier, this.cpuThreshold,
                this.memoryThreshold, this.externalRatioMultiplier,
                this.baselineCpuAvg, this.baselineHeapAvg,
                this.baselineSpanAvg, this.baselineExternalAvg,
                List.copyOf(snapshot.recentMetrics()),
                List.copyOf(snapshot.recentSpans()),
                List.copyOf(snapshot.recentExternalSpans())
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String appName;
        private PerformanceTrend trend;
        private double spikeMultiplier;
        private double cpuThreshold;
        private double memoryThreshold;
        private double externalRatioMultiplier;
        private double baselineCpuAvg;
        private double baselineHeapAvg;
        private double baselineSpanAvg;
        private double baselineExternalAvg;
        private List<MetricsSnapshot> recentMetrics = List.of();
        private List<SpanSnapshot> recentSpans = List.of();
        private List<ExternalSpanSnapshot> recentExternalSpans = List.of();

        public Builder appName(String appName) {
            this.appName = appName;
            return this;
        }

        public Builder trend(PerformanceTrend trend) {
            this.trend = trend;
            return this;
        }

        // ThresholdConfig 객체를 받아서 내부에서 분해
        public Builder threshold(ThresholdConfig threshold) {
            this.spikeMultiplier = threshold.spanDurationMultiplier();
            this.cpuThreshold = threshold.cpuThreshold();
            this.memoryThreshold = threshold.memoryThreshold();
            this.externalRatioMultiplier = threshold.externalRatioMultiplier();
            return this;
        }

        // BaselineMetrics 객체를 받아서 내부에서 분해
        public Builder baseline(BaselineMetrics baseline) {
            this.baselineCpuAvg = baseline.baselineCpuAvg();
            this.baselineHeapAvg = baseline.baselineHeapAvg();
            this.baselineSpanAvg = baseline.baselineSpanAvg();
            this.baselineExternalAvg = baseline.baselineExternalAvg();
            return this;
        }

        public AnalysisContext build() {
            return new AnalysisContext(
                    appName, trend,
                    spikeMultiplier, cpuThreshold, memoryThreshold,
                    externalRatioMultiplier,
                    baselineCpuAvg, baselineHeapAvg,
                    baselineSpanAvg, baselineExternalAvg,
                    recentMetrics, recentSpans, recentExternalSpans);
        }
    }

}