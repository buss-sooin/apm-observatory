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

/**
 * JWT 발급·검증을 담당한다. secret 문자열은 빈 생성 시점에 SecretKey로 한 번만 변환해 필드로
 * 보관하고 매 요청 재사용한다. role을 커스텀 클레임으로 실어 파싱 시 DB 조회 없이 권한을 얻는다.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /** username과 role로 JWT를 만든다. role은 커스텀 클레임이라 파싱 시 DB 조회 없이 추출된다. */
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

    /**
     * 토큰을 파싱하고 서명을 검증한다. 만료·위변조면 JwtException 계열이 올라가므로
     * 호출자(Filter)가 잡아 401로 처리한다.
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 토큰 유효성을 boolean으로 빠르게 확인한다. parse() 성공이면 서명 유효·미만료다. */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

}