package com.bokdeongi.backend.auth.service;

import com.bokdeongi.backend.auth.dto.request.PasswordResetCodeRequest;
import com.bokdeongi.backend.auth.dto.request.PasswordResetVerifyRequest;
import com.bokdeongi.backend.auth.dto.request.PasswordResetRequest;
import com.bokdeongi.backend.auth.entity.PasswordResetToken;
import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.repository.PasswordResetTokenRepository;
import com.bokdeongi.backend.auth.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 비밀번호 재설정 서비스 단위 테스트.
 *
 * 인증번호 발급 및 검증, 재설정 토큰 발급,
 * 비밀번호 변경 및 예외 상황을 검증한다.
 */
class PasswordResetServiceTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "test@example.com";
    private static final String OLD_PASSWORD_HASH = "oldPasswordHash";
    private static final String NEW_PASSWORD = "NewPassword123!";
    private static final String CODE = "012345";

    // 단위 테스트 전용 비밀키
    private static final String SECRET =
            "password-reset-unit-test-secret-key-123456789";

    private static final String RESET_TOKEN =
            Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(new byte[32]);

    private static final ZoneId KST =
            ZoneId.of("Asia/Seoul");

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordResetMailService mailService;
    private PasswordEncoder passwordEncoder;

    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        mailService = mock(PasswordResetMailService.class);
        passwordEncoder = mock(PasswordEncoder.class);

        service = new PasswordResetService(
                userRepository,
                tokenRepository,
                mailService,
                passwordEncoder,
                300,
                600,
                5,
                60,
                SECRET
        );

        user = mock(User.class);

        when(user.getId()).thenReturn(USER_ID);
        when(user.getEmail()).thenReturn(EMAIL);
        when(user.getPasswordHash())
                .thenReturn(OLD_PASSWORD_HASH);
    }

    /* ==================== 테스트 보조 메서드 ==================== */

    private LocalDateTime now() {
        return LocalDateTime.now(KST);
    }

    /**
     * 서비스와 동일한 방식으로 테스트용 해시를 생성한다.
     */
    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(
                    SECRET.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));

            return HexFormat.of().formatHex(
                    mac.doFinal(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String codeHash(String code) {
        return hmac(
                "password-reset-code:"
                        + USER_ID + ":" + code
        );
    }

    private String resetTokenHash() {
        return hmac(
                "password-reset-token:" + RESET_TOKEN
        );
    }

    /**
     * 정상 인증번호 기록을 생성한다.
     */
    private PasswordResetToken createRecord() {
        LocalDateTime now = now();

        return new PasswordResetToken(
                user,
                codeHash(CODE),
                now.plusMinutes(5),
                now
        );
    }

    /**
     * 이메일 회원 및 인증 기록 조회를 설정한다.
     */
    private void mockCodeRecord(PasswordResetToken record) {
        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user));

        when(tokenRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(record));
    }

    /**
     * 이메일 인증이 완료된 재설정 기록을 생성한다.
     */
    private PasswordResetToken createVerifiedRecord() {
        PasswordResetToken record = createRecord();

        record.markVerified(
                resetTokenHash(),
                now().plusMinutes(10),
                now()
        );

        return record;
    }

    /**
     * 비밀번호 재설정 요청의 회원 및 토큰 조회를 설정한다.
     */
    private void mockResetRecord(PasswordResetToken record) {
        when(tokenRepository.findUserIdByResetTokenHash(
                resetTokenHash()
        )).thenReturn(Optional.of(USER_ID));

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(user));

        when(tokenRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.of(record));
    }

    /* ==================== 인증번호 발급 테스트 ==================== */

    @Test
    void shouldSendResetCode() {
        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user));

        when(tokenRepository.findByUserIdForUpdate(USER_ID))
                .thenReturn(Optional.empty());

        var response = service.sendCode(
                new PasswordResetCodeRequest(EMAIL)
        );

        ArgumentCaptor<String> codeCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(mailService).sendResetCode(
                eq(EMAIL),
                codeCaptor.capture(),
                eq(300L)
        );

        String generatedCode = codeCaptor.getValue();

        assertTrue(generatedCode.matches("[0-9]{6}"));
        assertEquals(300, response.expiresIn());

        ArgumentCaptor<PasswordResetToken> recordCaptor =
                ArgumentCaptor.forClass(
                        PasswordResetToken.class
                );

        verify(tokenRepository)
                .saveAndFlush(recordCaptor.capture());

        PasswordResetToken savedRecord =
                recordCaptor.getValue();

        assertNotEquals(
                generatedCode,
                savedRecord.getCodeHash()
        );

        assertEquals(
                codeHash(generatedCode),
                savedRecord.getCodeHash()
        );
    }

    @Test
    void shouldReturnGenericResponseForUnknownEmail() {
        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.empty());

        var response = service.sendCode(
                new PasswordResetCodeRequest(EMAIL)
        );

        assertEquals(
                "가입된 이메일인 경우 인증번호가 발송됩니다.",
                response.message()
        );

        verifyNoInteractions(mailService, tokenRepository);
    }

    @Test
    void shouldNotSendCodeForKakaoOnlyAccount() {
        when(user.getPasswordHash()).thenReturn(null);

        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user));

        var response = service.sendCode(
                new PasswordResetCodeRequest(EMAIL)
        );

        assertEquals(300, response.expiresIn());

        verifyNoInteractions(mailService, tokenRepository);
    }

    @Test
    void shouldPreventRapidResend() {
        PasswordResetToken record = createRecord();

        mockCodeRecord(record);

        service.sendCode(
                new PasswordResetCodeRequest(EMAIL)
        );

        verifyNoInteractions(mailService);

        verify(tokenRepository, never())
                .saveAndFlush(any(PasswordResetToken.class));
    }

    @Test
    void shouldReissueCodeAfterResendInterval() {
        LocalDateTime previousTime =
                now().minusSeconds(61);

        PasswordResetToken record =
                new PasswordResetToken(
                        user,
                        codeHash(CODE),
                        previousTime.plusMinutes(5),
                        previousTime
                );

        record.markVerified(
                resetTokenHash(),
                now().plusMinutes(10),
                previousTime
        );

        record.increaseFailedAttempts();

        mockCodeRecord(record);

        service.sendCode(
                new PasswordResetCodeRequest(EMAIL)
        );

        assertEquals(0, record.getFailedAttempts());
        assertFalse(record.isVerified());
        assertNull(record.getResetTokenHash());
        assertNull(record.getResetExpiresAt());

        verify(tokenRepository).saveAndFlush(record);

        verify(mailService).sendResetCode(
                eq(EMAIL),
                anyString(),
                eq(300L)
        );
    }

    /* ==================== 인증번호 검증 테스트 ==================== */

    @Test
    void shouldVerifyCorrectCode() {
        PasswordResetToken record = createRecord();

        mockCodeRecord(record);

        var response = service.verifyCode(
                new PasswordResetVerifyRequest(
                        EMAIL,
                        CODE
                )
        );

        assertNotNull(response.resetToken());
        assertEquals(600, response.expiresIn());

        assertTrue(
                response.resetToken()
                        .matches("[A-Za-z0-9_-]{43}")
        );

        assertTrue(record.isVerified());

        assertNotEquals(
                response.resetToken(),
                record.getResetTokenHash()
        );

        assertEquals(
                hmac("password-reset-token:"
                        + response.resetToken()),
                record.getResetTokenHash()
        );
    }

    @Test
    void shouldIncreaseAttemptsForIncorrectCode() {
        PasswordResetToken record = createRecord();

        mockCodeRecord(record);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.verifyCode(
                                new PasswordResetVerifyRequest(
                                        EMAIL,
                                        "999999"
                                )
                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(1, record.getFailedAttempts());
        assertFalse(record.isVerified());
    }

    @Test
    void shouldRejectExpiredCode() {
        PasswordResetToken record =
                new PasswordResetToken(
                        user,
                        codeHash(CODE),
                        now().minusSeconds(1),
                        now().minusMinutes(6)
                );

        mockCodeRecord(record);

        assertThrows(
                ResponseStatusException.class,
                () -> service.verifyCode(
                        new PasswordResetVerifyRequest(
                                EMAIL,
                                CODE
                        )
                )
        );

        assertFalse(record.isVerified());
    }

    @Test
    void shouldRejectCodeAfterMaximumAttempts() {
        PasswordResetToken record = createRecord();

        for (int i = 0; i < 5; i++) {
            record.increaseFailedAttempts();
        }

        mockCodeRecord(record);

        assertThrows(
                ResponseStatusException.class,
                () -> service.verifyCode(
                        new PasswordResetVerifyRequest(
                                EMAIL,
                                CODE
                        )
                )
        );

        assertEquals(5, record.getFailedAttempts());
        assertFalse(record.isVerified());
    }

    @Test
    void shouldRejectAlreadyVerifiedCode() {
        PasswordResetToken record =
                createVerifiedRecord();

        mockCodeRecord(record);

        assertThrows(
                ResponseStatusException.class,
                () -> service.verifyCode(
                        new PasswordResetVerifyRequest(
                                EMAIL,
                                CODE
                        )
                )
        );
    }

    /* ==================== 비밀번호 재설정 테스트 ==================== */

    @Test
    void shouldResetPasswordSuccessfully() {
        PasswordResetToken record =
                createVerifiedRecord();

        mockResetRecord(record);

        when(passwordEncoder.matches(
                NEW_PASSWORD,
                OLD_PASSWORD_HASH
        )).thenReturn(false);

        when(passwordEncoder.encode(NEW_PASSWORD))
                .thenReturn("newEncodedPassword");

        service.resetPassword(
                new PasswordResetRequest(
                        RESET_TOKEN,
                        NEW_PASSWORD
                )
        );

        verify(user).changePassword(
                "newEncodedPassword"
        );

        verify(user).clearRefreshTokenHash();
        verify(user).increaseTokenVersion();
        verify(user).resetLoginFailures();

        assertTrue(record.isUsed());
        assertNull(record.getResetTokenHash());
    }

    @Test
    void shouldRejectExpiredResetToken() {
        PasswordResetToken record = createRecord();

        record.markVerified(
                resetTokenHash(),
                now().minusSeconds(1),
                now().minusMinutes(11)
        );

        mockResetRecord(record);

        assertThrows(
                ResponseStatusException.class,
                () -> service.resetPassword(
                        new PasswordResetRequest(
                                RESET_TOKEN,
                                NEW_PASSWORD
                        )
                )
        );

        verify(user, never())
                .changePassword(anyString());
    }

    @Test
    void shouldRejectUsedResetToken() {
        PasswordResetToken record =
                createVerifiedRecord();

        record.markUsed(now());

        mockResetRecord(record);

        assertThrows(
                ResponseStatusException.class,
                () -> service.resetPassword(
                        new PasswordResetRequest(
                                RESET_TOKEN,
                                NEW_PASSWORD
                        )
                )
        );

        verify(user, never())
                .changePassword(anyString());
    }

    @Test
    void shouldRejectSamePassword() {
        PasswordResetToken record =
                createVerifiedRecord();

        mockResetRecord(record);

        when(passwordEncoder.matches(
                NEW_PASSWORD,
                OLD_PASSWORD_HASH
        )).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.resetPassword(
                        new PasswordResetRequest(
                                RESET_TOKEN,
                                NEW_PASSWORD
                        )
                )
        );

        assertFalse(record.isUsed());

        verify(user, never())
                .changePassword(anyString());

        verify(passwordEncoder, never())
                .encode(anyString());
    }

    @Test
    void shouldRejectInvalidResetTokenFormat() {
        assertThrows(
                ResponseStatusException.class,
                () -> service.resetPassword(
                        new PasswordResetRequest(
                                "invalid-token",
                                NEW_PASSWORD
                        )
                )
        );

        verifyNoInteractions(
                userRepository,
                tokenRepository,
                passwordEncoder
        );
    }

    /* ==================== 서버 설정 테스트 ==================== */

    @Test
    void shouldRejectRequestWhenSecretIsMissing() {
        PasswordResetService invalidService =
                new PasswordResetService(
                        userRepository,
                        tokenRepository,
                        mailService,
                        passwordEncoder,
                        300,
                        600,
                        5,
                        60,
                        ""
                );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> invalidService.sendCode(
                                new PasswordResetCodeRequest(
                                        EMAIL
                                )
                        )
                );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                userRepository,
                tokenRepository,
                mailService
        );
    }
}