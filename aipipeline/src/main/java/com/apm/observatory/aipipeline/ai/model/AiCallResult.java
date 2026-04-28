package com.apm.observatory.aipipeline.ai.model;

// 의도: OllamaAnalysisService.call() 의 모든 결과 상태를 하나로 묶은 반환 타입
// 이 객체 하나만 보면 호출 결과의 상태, 날것 응답, 파싱 결과, 오류 메시지를 전부 알 수 있음
// result는 SUCCESS일 때만 존재, 나머지는 null
// AiAnalysisResultAdapter가 isSuccess()로 분기하여 저장 대상 테이블을 결정
public record AiCallResult(
        ParseStatus parseStatus,
        String rawResponse,       // Ollama 날것 응답 (AI_ERROR 시 null 가능)
        AiAnalysisResult result,  // 파싱 + 검증 성공 시에만 존재 (nullable)
        String errorMessage       // 실패 시 예외 메시지
) {

    // 의도: parseStatus 판단 책임을 record 자신이 가짐
    // Adapter는 이 메서드만 보고 저장 대상 테이블을 결정 → 판단 기준 캡슐화
    public boolean isSuccess() {
        return parseStatus == ParseStatus.SUCCESS;
    }

}