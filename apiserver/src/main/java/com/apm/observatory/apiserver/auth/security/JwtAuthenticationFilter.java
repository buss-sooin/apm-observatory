package com.apm.observatory.apiserver.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * 매 요청마다 한 번 실행되며(OncePerRequestFilter) JWT를 검증해 SecurityContext에 인증을
 * 채운다. Header → 토큰 추출 → 파싱 → DB 조회 → SecurityContext 저장 순으로 진행한다.
 * Filter는 DispatcherServlet 바깥이라 인증 실패 401은 sendUnauthorized에서 직접 작성한다.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   CustomUserDetailsService userDetailsService,
                                   ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        // 토큰이 없거나 무효면 인증 없이 통과 → 이후 SecurityConfig의 permitAll/authenticated가 판단
        if (token == null || !jwtTokenProvider.isValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims = jwtTokenProvider.parse(token);
        String username = claims.getSubject();

        try {
            // 토큰이 유효해도 계정이 삭제됐으면 여기서 UsernameNotFoundException
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,                        // JWT 방식이라 credentials 불필요
                            userDetails.getAuthorities()
                    );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (UsernameNotFoundException e) {
            // 토큰은 유효하나 계정이 삭제된 경우
            log.warn("유효한 토큰이나 존재하지 않는 계정: {}", username);
            sendUnauthorized(response, "존재하지 않는 계정입니다");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** "Authorization: Bearer {token}" 헤더에서 토큰 문자열만 떼어낸다. */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    /**
     * Filter 안에서 401 응답을 직접 작성한다. {@code @ControllerAdvice}는 DispatcherServlet
     * 안에서만 동작하고 Filter는 그 바깥이라, GlobalExceptionHandler가 아니라 여기서 응답한다.
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body = objectMapper.writeValueAsString(
                Map.of("code", "UNAUTHORIZED", "message", message)
        );
        response.getWriter().write(body);
    }

}