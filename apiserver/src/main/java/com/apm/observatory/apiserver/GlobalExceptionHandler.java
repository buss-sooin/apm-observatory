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

/**
 * Controller 계층에서 발생한 예외를 HTTP 응답으로 변환하는 단일 관문이다.
 * 클라이언트에는 의미 있는 메시지만 전달하고 내부 정보는 노출하지 않으며,
 * 서버 로그에는 스택트레이스를 남긴다.
 *
 * <p>{@code @RestControllerAdvice}는 {@code @ControllerAdvice}와 {@code @ResponseBody}를
 * 합친 것으로 모든 {@code @RestController}에 전역 적용된다.
 *
 * <p>Filter 계층 예외({@link com.apm.observatory.apiserver.auth.security.JwtAuthenticationFilter})는
 * 여기서 잡히지 않는다. Filter는 DispatcherServlet 바깥이라 그쪽에서 직접 401을 작성한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 잘못된 입력값 — 클라이언트 요청 문제. AuthService.register()의 중복 사용자명 등이 여기로 온다. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
    }

    /** {@code @RequestParam} 타입 불일치. 예: start_time=abc처럼 Instant 변환이 실패할 때 발생한다. */
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

    /** {@code @RequestBody} 파싱 실패. JSON 형식 오류나 필드 타입 불일치일 때 발생한다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e) {
        log.warn("요청 본문 파싱 실패: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST_BODY", "요청 본문의 형식이 올바르지 않습니다"));
    }

    /** 필수 {@code @RequestParam} 누락. 예: app_name 없이 /metrics/trend를 호출할 때 발생한다. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException e) {
        String message = String.format("필수 파라미터 '%s'가 누락되었습니다", e.getParameterName());
        log.warn("필수 파라미터 누락: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("MISSING_PARAMETER", message));
    }

    /**
     * 인증 실패 — 존재하지 않는 사용자. AuthenticationManager를 경유하는 로그인 경로에서만
     * 여기로 잡힌다. JwtAuthenticationFilter가 던지는 같은 예외는 Filter 계층이라
     * 여기서 잡히지 않고 Filter가 직접 401을 작성한다.
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFound(UsernameNotFoundException e) {
        log.warn("인증 실패: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", "인증에 실패했습니다"));
    }

    /**
     * 마지막 방어선 — 예상치 못한 모든 예외. 클라이언트에는 고정 메시지만 주고
     * 서버 로그에는 스택트레이스를 포함해 전체를 기록한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("예상치 못한 오류 발생: {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다"));
    }

    /** 클라이언트 응답 형식. 스택트레이스·내부 패키지 정보 같은 민감 정보는 담지 않는다. */
    public record ErrorResponse(String code, String message) {}

}