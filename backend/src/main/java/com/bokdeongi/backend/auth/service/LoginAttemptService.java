package com.bokdeongi.backend.auth.service;

import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 일반 이메일 로그인 시 인증 검증 및 실패 횟수를 관리하는 서비스.
 *
 * 비밀번호 검증, 계정 잠금 및 로그인 실패 기록 초기화를 처리하며,
 * 독립적인 트랜잭션을 통해 로그인 실패 기록을 유지한다.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public enum LoginStatus {
        SUCCESS,
        INVALID_CREDENTIALS,
        LOCKED
    }

    public record LoginAttemptResult(
            LoginStatus status,
            Long userId
    ) {}

    /**
     * 로그인 실패 기록을 독립적인 트랜잭션에서 저장한다.
     *
     * 인증 결과는 반환하고, 예외는 호출한 서비스에서 발생시킨다.
     * 이를 통해 로그인 실패 횟수가 예외 때문에 롤백되지 않도록 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoginAttemptResult attempt(
            String email,
            String password
    ) {
        // 회원 행을 잠가 동시 로그인 시도의 순서를 보장한다.
        User user = userRepository.findByEmailForUpdate(email)
                .orElse(null);

        // 이메일이 존재하지 않는 경우
        if (user == null) {
            return new LoginAttemptResult(
                    LoginStatus.INVALID_CREDENTIALS,
                    null
            );
        }

        // 카카오 전용 계정 등 비밀번호가 없는 계정
        if (user.getPasswordHash() == null) {
            return new LoginAttemptResult(
                    LoginStatus.INVALID_CREDENTIALS,
                    null
            );
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        // 계정 잠금 시간이 아직 지나지 않은 경우
        if (user.isLoginLocked(now)) {
            return new LoginAttemptResult(
                    LoginStatus.LOCKED,
                    null
            );
        }

        // 잠금 시간이 만료된 경우 기록 초기화
        if (user.getLockedUntil() != null) {
            user.resetLoginFailures();
        }

        // 비밀번호가 일치하지 않는 경우
        if (!passwordEncoder.matches(
                password,
                user.getPasswordHash()
        )) {
            user.recordFailedLogin(now);

            // 다섯 번째 실패부터 계정 잠금
            LoginStatus status = user.isLoginLocked(now)
                    ? LoginStatus.LOCKED
                    : LoginStatus.INVALID_CREDENTIALS;

            return new LoginAttemptResult(status, null);
        }

        // 정상 로그인 시 실패 횟수 초기화
        user.resetLoginFailures();

        return new LoginAttemptResult(
                LoginStatus.SUCCESS,
                user.getId()
        );
    }
}