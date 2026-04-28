package com.apm.observatory.apiserver.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expiration;

    // 의도: 빈 생성 시점에 secret 문자열을 SecretKey 객체로 변환해서 보관
    // 매 요청마다 변환하지 않고 한 번만 변환해서 재사용
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    // 의도: username + role → JWT 문자열 생성
    // role은 커스텀 클레임으로 저장 → 파싱 시 DB 조회 없이 role 추출 가능
    public String generate(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    // 의도: 토큰 파싱 + 서명 검증
    // 만료/위변조 시 JwtException 계열 예외 발생 → 호출자(Filter)가 catch해서 401 처리
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 의도: 필터에서 토큰 유효성을 boolean으로 빠르게 확인하는 진입점
    // parse() 성공 = 서명 유효 + 만료 안됨 → true
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

}