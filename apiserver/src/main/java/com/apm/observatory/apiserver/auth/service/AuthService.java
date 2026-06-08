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

/**
 * 인증 위임과 계정 생성을 담당한다. 접근 제어(누가 어떤 API를 부를 수 있는가)는
 * SecurityConfig가 맡고, 여기서는 인증과 저장 책임만 진다.
 */
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

    /**
     * 인증을 AuthenticationManager에 위임한다. 내부적으로
     * CustomUserDetailsService.loadUserByUsername()을 호출해 BCrypt로 비교하고, 실패하면
     * AuthenticationException이 올라가 Spring Security가 401로 응답한다.
     */
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

    /**
     * 계정을 생성한다. 신규 계정은 항상 ROLE_VIEWER로 고정하며, ADMIN 계정은 init.sql에서만
     * 만든다. 이 API를 ADMIN만 호출할 수 있게 막는 것은 SecurityConfig의 몫이다.
     */
    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("이미 존재하는 사용자명입니다: " + request.username());
        }

        UserEntity user = new UserEntity(
                UUID.randomUUID().toString(),
                request.username(),
                passwordEncoder.encode(request.password()),
                "ROLE_VIEWER"
        );

        userRepository.save(user);
    }

}