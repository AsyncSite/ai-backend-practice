package com.gritmoments.backend.user.service;

import com.gritmoments.backend.common.exception.BusinessException;
import com.gritmoments.backend.common.exception.ResourceNotFoundException;
import com.gritmoments.backend.user.entity.User;
import com.gritmoments.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 서비스 (세션 07: 인증/인가)
 *
 * 사용자 등록 및 조회를 담당합니다.
 * - 비밀번호는 BCrypt로 암호화하여 저장 (세션 07: Spring Security)
 * - 이메일 중복 검증
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 회원가입 (세션 07: BCrypt 암호화)
     */
    @Transactional
    public User signup(String email, String password, String name, String phone) {
        // 이메일 중복 검증
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("이미 사용 중인 이메일입니다: " + email);
        }

        // 비밀번호 암호화 (BCrypt)
        String encodedPassword = passwordEncoder.encode(password);

        User user = User.builder()
                .email(email)
                .password(encodedPassword)
                .name(name)
                .phone(phone)
                .role(User.Role.CUSTOMER)
                .build();

        User savedUser = userRepository.save(user);
        log.info("회원가입 완료: {} ({})", savedUser.getName(), savedUser.getEmail());
        return savedUser;
    }

    /**
     * 이메일로 사용자 조회 (세션 07: 인증 시 사용)
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    /**
     * ID로 사용자 조회
     */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
