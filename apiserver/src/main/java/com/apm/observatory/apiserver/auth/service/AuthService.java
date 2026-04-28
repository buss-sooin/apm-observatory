package com.apm.observatory.apiserver.auth.service;

import com.apm.observatory.apiserver.auth.entity.UserEntity;
import com.apm.observatory.apiserver.auth.model.AuthModel.LoginRequest;
import com.apm.observatory.apiserver.auth.model.AuthModel.LoginResponse;
import com.apm.observatory.apiserver.auth.model.AuthModel.RegisterRequest;
import com.apm.observatory.apiserver.auth.repository.UserRepository;
import com.apm.observatory.apiserver.auth.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 의도: AuthenticationManager에게 인증을 위임
    // 내부적으로 CustomUserDetailsService.loadUserByUsername() 호출 → BCrypt 비교
    // 실패 시 AuthenticationException 발생 → Spring Security가 401 처리
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_VIEWER");

        String token = jwtTokenProvider.generate(request.username(), role);
        return new LoginResponse(token);
    }

    // 의도: ADMIN이 VIEWER 계정 생성
    // 접근 제어는 SecurityConfig에서 담당 → 여기선 저장 책임만
    // 신규 계정은 항상 ROLE_VIEWER로 고정 (ADMIN 계정은 init.sql에서만 생성)
    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("이미 존재하는 사용자명입니다: " + request.username());
        }

        UserEntity user = new UserEntity(
                UUID.randomUUID().toString(),
                request.username(),
                passwordEncoder.encode(request.password()),
                "ROLE_VIEWER"  // 의도: register API로는 VIEWER만 생성 가능
        );

        userRepository.save(user);
    }

}