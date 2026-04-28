package com.apm.observatory.aipipeline.analysis.evaluator;

import com.apm.observatory.aipipeline.analysis.status.DetectionStatus;
import com.apm.observatory.aipipeline.analysis.status.TrendStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class PerformanceErosionEvaluator {

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

    public TrendStatus toTrendStatus(double slope, double slopeMinPositive) {
        if (Double.isNaN(slope)) {
            log.warn("추세 판단 스킵 - 기울기 계산 불가");
            return TrendStatus.NODATA;
        }

        TrendStatus status = slope > slopeMinPositive ? TrendStatus.RISING : TrendStatus.FLAT;
        log.debug("추세 판단 완료 slope={}, slopeMinPositive={}, status={}", slope, slopeMinPositive, status);
        return status;
    }

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