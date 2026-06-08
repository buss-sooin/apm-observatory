package com.apm.observatory.apiserver.auth.security;

import com.apm.observatory.apiserver.auth.entity.UserEntity;
import com.apm.observatory.apiserver.auth.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * username으로 DB를 조회해 Spring Security가 이해하는 UserDetails로 변환한다
 * (UserDetailsService 구현). JwtAuthenticationFilter가 매 요청마다 호출하므로 계정 존재
 * 여부가 실시간으로 확인된다.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 사용자를 찾지 못하면 UsernameNotFoundException을 던진다. */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        return new User(
                user.getUsername(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(user.getRole()))
        );
    }

}