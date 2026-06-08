package com.apm.observatory.aipipeline.analysis.evaluator;

import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.analysis.status.TrendStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 성능 침식 판정기. 누적 시계열의 기울기를 선형 회귀로 구하고, 자원·응답 추세가
 * 모두 상승할 때만 침식으로 판정한다.
 */
@Slf4j
@Component
public class PerformanceErosionEvaluator {

    /** 값 시계열의 기울기를 단순 선형 회귀로 구한다. 포인트가 2개 미만이면 계산 불가로 NaN. */
    public double calculateSlope(List<Double> values) {
        if (values.size() < 2) {
            log.warn("추세 계산 스킵 - 데이터 포인트 부족 size={}", values.size());
            return Double.NaN;
        }

        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i < values.size(); i++) {
            regression.addData(i, values.get(i));
        }

        double slope = regression.getSlope();
        log.debug("기울기 계산 완료 slope={}", slope);
        return slope;
    }

    /** 기울기가 {@code slopeMinPositive}를 넘으면 RISING, 그 이하면 FLAT. NaN이면 NODATA. */
    public TrendStatus toTrendStatus(double slope, double slopeMinPositive) {
        if (Double.isNaN(slope)) {
            log.warn("추세 판단 스킵 - 기울기 계산 불가");
            return TrendStatus.NODATA;
        }

        TrendStatus status = slope > slopeMinPositive ? TrendStatus.RISING : TrendStatus.FLAT;
        log.debug("추세 판단 완료 slope={}, slopeMinPositive={}, status={}", slope, slopeMinPositive, status);
        return status;
    }

    /** 자원·응답 추세가 모두 RISING일 때만 DETECTED. NODATA 포함 시 UNDETERMINABLE. */
    public DetectionStatus evaluate(TrendStatus resourceTrend, TrendStatus responseTrend) {
        if (resourceTrend == TrendStatus.NODATA || responseTrend == TrendStatus.NODATA) {
            log.warn("판단 불가 - NODATA 상태 포함 resource={}, response={}",
                    resourceTrend, responseTrend);
            return DetectionStatus.UNDETERMINABLE;
        }

        DetectionStatus result = (resourceTrend == TrendStatus.RISING &&
                responseTrend == TrendStatus.RISING)
                ? DetectionStatus.DETECTED
                : DetectionStatus.NOT_DETECTED;

        log.info("PerformanceErosion 판단 완료 resource={}, response={}, result={}",
                resourceTrend, responseTrend, result);

        return result;
    }

}