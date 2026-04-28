package com.apm.observatory.apiserver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// 역할: Controller 계층 예외 → HTTP 응답 변환 단일 관문
// 클라이언트: 의미있는 메시지만 전달 (내부 정보 노출 금지)
// 서버 로그: 상세 스택트레이스 기록
//
// @RestControllerAdvice = @ControllerAdvice + @ResponseBody
// 모든 @RestController에 전역 적용
//
// 주의: Filter 계층 예외(JwtAuthenticationFilter)는 여기서 잡히지 않음
//       Filter는 DispatcherServlet 바깥이라 별도 처리 필요
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 잘못된 입력값 — 클라이언트 요청 문제
    // AuthService.register() 중복 사용자명 등
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
    }

    // @RequestParam 타입 불일치
    // Instant 파라미터에 잘못된 형식 전달 시 발생
    // 예: start_time=abc → Instant 변환 실패
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        String message = String.format(
                "파라미터 '%s'의 형식이 올바르지 않습니다. 기대 타입: %s",
                e.getName(),
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "알 수 없음"
        );
        log.warn("파라미터 타입 오류: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_PARAMETER_TYPE", message));
    }

    // @RequestBody 파싱 실패
    // JSON 형식 오류, 필드 타입 불일치 시 발생
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e) {
        log.warn("요청 본문 파싱 실패: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST_BODY", "요청 본문의 형식이 올바르지 않습니다"));
    }

    // 필수 @RequestParam 누락
    // 예: app_name 없이 /metrics/trend 호출
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException e) {
        String message = String.format("필수 파라미터 '%s'가 누락되었습니다", e.getParameterName());
        log.warn("필수 파라미터 누락: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("MISSING_PARAMETER", message));
    }

    // 인증 실패 — 존재하지 않는 사용자
    // JwtAuthenticationFilter의 loadUserByUsername() 실패 시
    // 단, Filter 예외는 여기서 잡히지 않음
    // AuthenticationManager 경유 시(login)에만 여기서 잡힘
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFound(UsernameNotFoundException e) {
        log.warn("인증 실패: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", "인증에 실패했습니다"));
    }

    // 마지막 방어선 — 예상치 못한 모든 예외
    // 클라이언트에게 내부 정보 노출 없이 고정 메시지
    // 서버 로그에는 스택트레이스 포함 전체 기록
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("예상치 못한 오류 발생: {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다"));
    }

    // 클라이언트 응답 형식
    // record: 불변 데이터 전달 목적에 적합
    // 스택트레이스, 내부 패키지 정보 등 민감 정보 포함하지 않음
    public record ErrorResponse(String code, String message) {}

}