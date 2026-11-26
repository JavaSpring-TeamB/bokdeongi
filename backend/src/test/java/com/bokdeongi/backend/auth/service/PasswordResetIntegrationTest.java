package com.bokdeongi.backend.auth.service;

import com.bokdeongi.backend.auth.dto.request.PasswordResetCodeRequest;
import com.bokdeongi.backend.auth.dto.request.PasswordResetVerifyRequest;
import com.bokdeongi.backend.auth.dto.request.PasswordResetRequest;
import com.bokdeongi.backend.auth.dto.request.PasswordChangeRequest;
import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.entity.PasswordResetToken;
import com.bokdeongi.backend.auth.repository.UserRepository;
import com.bokdeongi.backend.auth.repository.PasswordResetTokenRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 비밀번호 재설정 기능 통합 테스트.
 *
 * 실제 Spring 트랜잭션과 H2 데이터베이스를 이용하여
 * 인증 정보 저장, 실패 횟수 유지, 비밀번호 변경 및
 * 재설정 토큰의 일회성 사용을 검증한다.
 *
 * 실제 이메일 발송은 Mock으로 대체한다.
 */
@SpringBootTest(properties = {
        "app.password-reset.secret=integration-test-only-secret-key-123456789",
        "app.password-reset.code-expiration-seconds=300",
        "app.password-reset.token-expiration-seconds=600",
        "app.password-reset.max-attempts=5",
        "app.password-reset.resend-interval-seconds=60"
})
@ActiveProfiles("test")
class PasswordResetIntegrationTest {

    private static final String OLD_PASSWORD =
            "OldPassword123!";

    private static final String NEW_PASSWORD =
            "NewPassword123!";

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private PasswordResetMailService mailService;

    private Long userId;
    private String email;

    /**
     * 각 테스트에서 독립적인 이메일 회원을 생성한다.
     */
    @BeforeEach
    void setUp() {
        email = "password-reset-"
                + UUID.randomUUID()
                + "@example.com";

        User user = userRepository.saveAndFlush(
                new User(
                        email,
                        passwordEncoder.encode(OLD_PASSWORD),
                        "비밀번호찾기테스트",
                        "01000000000",
                        null
                )
        );

        userId = user.getId();
    }

    /**
     * 테스트에서 생성한 인증 기록과 회원을 삭제한다.
     */
    @AfterEach
    void tearDown() {
        if (userId == null) {
            return;
        }

        tokenRepository.findByUserId(userId)
                .ifPresent(tokenRepository::delete);

        userRepository.deleteById(userId);
    }

    /* ==================== 테스트 보조 메서드 ==================== */

    /**
     * 인증번호를 발급받고 이메일 발송 Mock에서
     * 실제 생성된 인증번호를 가져온다.
     */
    private String issueCode() {
        passwordResetService.sendCode(
                new PasswordResetCodeRequest(email)
        );

        ArgumentCaptor<String> captor =
                ArgumentCaptor.forClass(String.class);

        verify(mailService).sendResetCode(
                eq(email),
                captor.capture(),
                eq(300L)
        );

        return captor.getValue();
    }

    /**
     * 인증번호 발급 및 검증을 완료하고
     * 일회용 비밀번호 재설정 토큰을 가져온다.
     */
    private String issueResetToken() {
        String code = issueCode();

        return passwordResetService.verifyCode(
                new PasswordResetVerifyRequest(
                        email,
                        code
                )
        ).resetToken();
    }

    /**
     * 실제 DB에 저장된 인증 기록을 조회한다.
     */
    private PasswordResetToken findRecord() {
        return tokenRepository.findByUserId(userId)
                .orElseThrow();
    }

    /**
     * 발급된 인증번호와 다른 인증번호를 생성한다.
     */
    private String wrongCode(String code) {
        return "000000".equals(code)
                ? "111111"
                : "000000";
    }

    /* ==================== 통합 테스트 ==================== */

    /**
     * 인증번호가 평문이 아닌 해시 형태로 DB에 저장된다.
     */
    @Test
    void shouldStoreHashedCodeInDatabase() {
        String code = issueCode();

        PasswordResetToken record = findRecord();

        assertNotNull(record.getId());
        assertTrue(code.matches("[0-9]{6}"));

        assertNotEquals(
                code,
                record.getCodeHash()
        );

        assertTrue(
                record.getCodeHash()
                        .matches("[0-9a-f]{64}")
        );

        assertEquals(0, record.getFailedAttempts());
        assertNull(record.getVerifiedAt());
        assertNull(record.getUsedAt());
    }

    /**
     * 인증번호 검증 실패 예외가 발생해도
     * 실패 횟수가 실제 DB에 유지된다.
     */
    @Test
    void shouldPersistFailedAttemptsAfterException() {
        String code = issueCode();

        assertThrows(
                ResponseStatusException.class,
                () -> passwordResetService.verifyCode(
                        new PasswordResetVerifyRequest(
                                email,
                                wrongCode(code)
                        )
                )
        );

        PasswordResetToken record = findRecord();

        assertEquals(1, record.getFailedAttempts());
        assertNull(record.getVerifiedAt());
    }

    /**
     * 인증번호 입력에 5회 실패하면
     * 올바른 인증번호를 입력해도 인증을 제한한다.
     */
    @Test
    void shouldRejectVerificationAfterFiveFailures() {
        String code = issueCode();

        for (int i = 0; i < 5; i++) {
            assertThrows(
                    ResponseStatusException.class,
                    () -> passwordResetService.verifyCode(
                            new PasswordResetVerifyRequest(
                                    email,
                                    wrongCode(code)
                            )
                    )
            );
        }

        assertEquals(
                5,
                findRecord().getFailedAttempts()
        );

        assertThrows(
                ResponseStatusException.class,
                () -> passwordResetService.verifyCode(
                        new PasswordResetVerifyRequest(
                                email,
                                code
                        )
                )
        );

        assertFalse(findRecord().isVerified());
    }

    /**
     * 이메일 인증 후 비밀번호를 재설정하면
     * 새 비밀번호가 암호화되어 실제 DB에 저장된다.
     */
    @Test
    void shouldResetPasswordInDatabase() {
        String resetToken = issueResetToken();

        User before = userRepository.findById(userId)
                .orElseThrow();

        int previousTokenVersion =
                before.getTokenVersion();

        passwordResetService.resetPassword(
                new PasswordResetRequest(
                        resetToken,
                        NEW_PASSWORD
                )
        );

        User updated = userRepository.findById(userId)
                .orElseThrow();

        assertTrue(
                passwordEncoder.matches(
                        NEW_PASSWORD,
                        updated.getPasswordHash()
                )
        );

        assertFalse(
                passwordEncoder.matches(
                        OLD_PASSWORD,
                        updated.getPasswordHash()
                )
        );

        assertNotEquals(
                NEW_PASSWORD,
                updated.getPasswordHash()
        );

        assertTrue(
                updated.getTokenVersion()
                        > previousTokenVersion
        );

        PasswordResetToken record = findRecord();

        assertTrue(record.isUsed());
        assertNull(record.getResetTokenHash());
    }

    /**
     * 이미 사용한 재설정 토큰은 재사용할 수 없다.
     */
    @Test
    void shouldPreventResetTokenReuse() {
        String resetToken = issueResetToken();

        passwordResetService.resetPassword(
                new PasswordResetRequest(
                        resetToken,
                        NEW_PASSWORD
                )
        );

        assertThrows(
                ResponseStatusException.class,
                () -> passwordResetService.resetPassword(
                        new PasswordResetRequest(
                                resetToken,
                                "AnotherPassword123!"
                        )
                )
        );

        User updated = userRepository.findById(userId)
                .orElseThrow();

        assertTrue(
                passwordEncoder.matches(
                        NEW_PASSWORD,
                        updated.getPasswordHash()
                )
        );
    }

    /**
     * 이메일 발송에 실패하면 신규 인증 기록 저장도
     * 트랜잭션에 의해 롤백된다.
     */
    @Test
    void shouldRollbackWhenMailSendingFails() {
        doThrow(new IllegalStateException(
                "인증번호 이메일 발송 실패"
        )).when(mailService).sendResetCode(
                eq(email),
                anyString(),
                eq(300L)
        );

        assertThrows(
                IllegalStateException.class,
                () -> passwordResetService.sendCode(
                        new PasswordResetCodeRequest(email)
                )
        );

        assertTrue(
                tokenRepository.findByUserId(userId)
                        .isEmpty()
        );
    }

    /**
     * 기존 로그인 상태 비밀번호 변경 기능을 사용하면
     * 이전에 발급받은 재설정 인증 기록이 삭제된다.
     */
    @Test
    void shouldInvalidateResetTokenAfterPasswordChange() {
        String resetToken = issueResetToken();

        authService.changePassword(
                userId,
                new PasswordChangeRequest(
                        OLD_PASSWORD,
                        NEW_PASSWORD
                )
        );

        assertTrue(
                tokenRepository.findByUserId(userId)
                        .isEmpty()
        );

        assertThrows(
                ResponseStatusException.class,
                () -> passwordResetService.resetPassword(
                        new PasswordResetRequest(
                                resetToken,
                                "AnotherPassword123!"
                        )
                )
        );
    }

    /**
     * 동일한 재설정 토큰으로 동시에 요청하더라도
     * 하나의 요청만 성공해야 한다.
     */
    @Test
    void shouldAllowOnlyOneConcurrentPasswordReset()
            throws Exception {

        String resetToken = issueResetToken();

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready =
                new CountDownLatch(2);

        CountDownLatch start =
                new CountDownLatch(1);

        Callable<Boolean> task = () -> {
            ready.countDown();

            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "동시 요청 시작 시간 초과"
                );
            }

            try {
                passwordResetService.resetPassword(
                        new PasswordResetRequest(
                                resetToken,
                                NEW_PASSWORD
                        )
                );

                return true;

            } catch (ResponseStatusException e) {
                assertEquals(
                        400,
                        e.getStatusCode().value()
                );

                return false;
            }
        };

        try {
            Future<Boolean> first =
                    executor.submit(task);

            Future<Boolean> second =
                    executor.submit(task);

            assertTrue(
                    ready.await(5, TimeUnit.SECONDS)
            );

            start.countDown();

            boolean firstResult =
                    first.get(20, TimeUnit.SECONDS);

            boolean secondResult =
                    second.get(20, TimeUnit.SECONDS);

            assertNotEquals(
                    firstResult,
                    secondResult
            );

            assertTrue(findRecord().isUsed());

            User updated = userRepository.findById(userId)
                    .orElseThrow();

            assertTrue(
                    passwordEncoder.matches(
                            NEW_PASSWORD,
                            updated.getPasswordHash()
                    )
            );

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}