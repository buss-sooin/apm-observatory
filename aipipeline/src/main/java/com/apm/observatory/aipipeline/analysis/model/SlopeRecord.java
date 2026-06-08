package com.apm.observatory.aipipeline.analysis.model;

/**
 * 추세 판정에 쓰는 데이터 묶음. slope(자원·응답)와 판단 기준값(slopeMinPositive)을
 * 함께 담아, 호출자가 한 번 계산한 값을 추세 전략에 그대로 넘긴다.
 */
public record SlopeRecord(
        String appName,
        double resourceSlope,
        double responseSlope,
        double slopeMinPositive
) {}