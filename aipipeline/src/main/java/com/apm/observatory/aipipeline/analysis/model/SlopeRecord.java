package com.apm.observatory.aipipeline.analysis.model;

// 의도: TransferStep에서 미리 계산한 slope 값과 판단 기준값을 전략에 전달하는 data only 객체
// 계산 책임은 TransferStep, 저장/판단 책임은 ErosionDetectionStrategy
// slopeMinPositive 포함 이유: TransferStep 한 곳에서만 설정값을 알면 됨
//   → 기존엔 TransferStep → evaluateTrend → detectTrend → toTrendStatus 까지 4번 전달
//   → SlopeRecord에 캡슐화해서 전달 경로 단순화
public record SlopeRecord(
        String appName,
        double resourceSlope,
        double responseSlope,
        double slopeMinPositive
) {}