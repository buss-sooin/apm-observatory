package com.apm.observatory.aipipeline.analyzer;

import com.apm.observatory.aipipeline.performance.model.PerformanceTrend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PerformanceTrend는 설정된 수명이 지나면 만료되어 Erosion 판단을 트리거한다")
class PerformanceTrendTest {

    @Test
    @DisplayName("수명이 지나지 않았으면 만료되지 않는다")
    void 수명이_지나지_않으면_만료_아님() {
        Instant start = Instant.now().minus(29, ChronoUnit.MINUTES);
        PerformanceTrend trend = new PerformanceTrend("test-app", start);

        // 29분 경과, 수명 30분 → 만료 아님
        assertThat(trend.isExpired(Instant.now(), 30)).isFalse();
    }

    @Test
    @DisplayName("수명이 정확히 지났으면 만료된다")
    void 수명이_정확히_지나면_만료() {
        Instant start = Instant.now().minus(30, ChronoUnit.MINUTES);
        PerformanceTrend trend = new PerformanceTrend("test-app", start);

        // 30분 경과, 수명 30분 → 만료
        assertThat(trend.isExpired(Instant.now(), 30)).isTrue();
    }

    @Test
    @DisplayName("수명이 초과됐으면 만료된다")
    void 수명이_초과되면_만료() {
        Instant start = Instant.now().minus(31, ChronoUnit.MINUTES);
        PerformanceTrend trend = new PerformanceTrend("test-app", start);

        // 31분 경과, 수명 30분 → 만료
        assertThat(trend.isExpired(Instant.now(), 30)).isTrue();
    }

}