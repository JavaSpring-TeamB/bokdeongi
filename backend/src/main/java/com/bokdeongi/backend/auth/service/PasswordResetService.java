package com.bokdeongi.backend.auth.service;

import com.bokdeongi.backend.auth.dto.request.PasswordResetCodeRequest;
import com.bokdeongi.backend.auth.dto.request.PasswordResetVerifyRequest;
import com.bokdeongi.backend.auth.dto.request.PasswordResetRequest;
import com.bokdeongi.backend.auth.dto.response.PasswordResetCodeResponse;
import com.bokdeongi.backend.auth.dto.response.PasswordResetVerifyResponse;
import com.bokdeongi.backend.auth.entity.PasswordResetToken;
import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.repository.PasswordResetTokenRepository;
import com.bokdeongi.backend.auth.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 비밀번호 재설정 서비스.
 *
 * 이메일 인증번호 발급 및 검증, 일회용 재설정 토큰 발급,
 * 비밀번호 변경 및 기존 로그인 토큰 무효화를 처리한다.
 */
@Service
public class PasswordResetService {

    private static final ZoneId KST =
            ZoneId.of("Asia/Seoul");

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private static final String SEND_MESSAGE =
            "가입된 이메일인 경우 인증번호가 발송됩니다.";

    private static final String INVALID_CODE_MESSAGE =
            "인증번호가 올바르지 않거나 만료되었습니다.";

    private static final String INVALID_TOKEN_MESSAGE =
            "유효하지 않거나 만료된 재설정 토큰입니다.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetMailService mailService;
    private final PasswordEncoder passwordEncoder;

    private final long codeExpirationSeconds;
    private final long tokenExpirationSeconds;
    private final int maxAttempts;
    private final long resendIntervalSeconds;
    private final String secret;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordResetMailService mailService,
            PasswordEncoder passwordEncoder,
            @Value("${app.password-reset.code-expiration-seconds:300}")
            long codeExpirationSeconds,
            @Value("${app.password-reset.token-expiration-seconds:600}")
            long tokenExpirationSeconds,
            @Value("${app.password-reset.max-attempts:5}")
            int maxAttempts,
            @Value("${app.password-reset.resend-interval-seconds:60}")
            long resendIntervalSeconds,
            @Value("${app.password-reset.secret:}")
            String secret
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;

        this.codeExpirationSeconds = codeExpirationSeconds;
        this.tokenExpirationSeconds = tokenExpirationSeconds;
        this.maxAttempts = maxAttempts;
        this.resendIntervalSeconds = resendIntervalSeconds;
        this.secret = secret;
    }

    /* ==================== 인증번호 발급 ==================== */

    /**
     * 가입 이메일로 비밀번호 재설정 인증번호를 발송한다.
     *
     * 계정 존재 여부가 노출되지 않도록 공통 응답을 반환한다.
     */
    @Transactional
    public PasswordResetCodeResponse sendCode(
            PasswordResetCodeRequest request
    ) {
        validateConfiguration();

        PasswordResetCodeResponse response =
                new PasswordResetCodeResponse(
                        SEND_MESSAGE,
                        codeExpirationSeconds
                );

        // 회원 행 쓰기 잠금
        User user = userRepository
                .findByEmailForUpdate(request.email())
                .orElse(null);

        // 미가입 이메일 및 비밀번호 없는 계정은
        // 실제 발송 없이 동일한 응답을 반환한다.
        if (user == null || user.getPasswordHash() == null) {
            return response;
        }

        PasswordResetToken record = tokenRepository
                .findByUserIdForUpdate(user.getId())
                .orElse(null);

        LocalDateTime now = LocalDateTime.now(KST);

        // 인증번호 재발송 제한
        if (record != null
                && !record.canResend(now, resendIntervalSeconds)) {
            return response;
        }

        String code = generateCode();

        String codeHash = hashCode(
                user.getId(),
                code
        );

        LocalDateTime expiresAt =
                now.plusSeconds(codeExpirationSeconds);

        // 신규 인증 기록 생성 또는 기존 기록 갱신
        if (record == null) {
            record = new PasswordResetToken(
                    user,
                    codeHash,
                    expiresAt,
                    now
            );
        } else {
            record.reissue(
                    codeHash,
                    expiresAt,
                    now
            );
        }

        tokenRepository.saveAndFlush(record);

        // 발송 실패 시 예외가 발생하고 트랜잭션이 롤백된다.
        mailService.sendResetCode(
                user.getEmail(),
                code,
                codeExpirationSeconds
        );

        return response;
    }

    /* ==================== 인증번호 검증 ==================== */

    /**
     * 인증번호를 검증하고 일회용 재설정 토큰을 발급한다.
     *
     * 인증 실패 횟수는 예외 발생 시에도 DB에 반영되도록 한다.
     */
    @Transactional(
            noRollbackFor = InvalidResetCodeException.class
    )
    public PasswordResetVerifyResponse verifyCode(
            PasswordResetVerifyRequest request
    ) {
        validateConfiguration();

        User user = userRepository
                .findByEmailForUpdate(request.email())
                .orElseThrow(this::invalidCode);

        if (user.getPasswordHash() == null) {
            throw invalidCode();
        }

        PasswordResetToken record = tokenRepository
                .findByUserIdForUpdate(user.getId())
                .orElseThrow(this::invalidCode);

        LocalDateTime now = LocalDateTime.now(KST);

        // 만료, 인증 완료, 사용 완료 및 실패 횟수 검증
        if (record.isCodeExpired(now)
                || record.isVerified()
                || record.isUsed()
                || record.hasReachedMaxAttempts(maxAttempts)) {

            throw invalidCode();
        }

        String requestedCodeHash = hashCode(
                user.getId(),
                request.code()
        );

        // 인증번호가 일치하지 않으면 실패 횟수 증가
        if (!matchesHash(
                record.getCodeHash(),
                requestedCodeHash
        )) {
            record.increaseFailedAttempts();

            throw invalidCode();
        }

        // 인증 성공 시 일회용 재설정 토큰 생성
        String resetToken = generateResetToken();

        record.markVerified(
                hashResetToken(resetToken),
                now.plusSeconds(tokenExpirationSeconds),
                now
        );

        return new PasswordResetVerifyResponse(
                resetToken,
                tokenExpirationSeconds
        );
    }

    /* ==================== 비밀번호 재설정 ==================== */

    /**
     * 일회용 재설정 토큰을 검증하고 새로운 비밀번호를 저장한다.
     *
     * 기존 로그인 토큰을 무효화하고 로그인 실패 횟수를 초기화한다.
     */
    @Transactional
    public void resetPassword(
            PasswordResetRequest request
    ) {
        validateConfiguration();

        validateNewPassword(request.newPassword());

        // 정상 발급된 토큰의 형식인지 확인한다.
        if (request.resetToken() == null
                || !request.resetToken()
                .matches("[A-Za-z0-9_-]{43}")) {
            throw invalidToken();
        }

        String tokenHash =
                hashResetToken(request.resetToken());

        // 회원 ID만 조회하여 잠금 순서를 유지한다.
        Long userId = tokenRepository
                .findUserIdByResetTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);

        // 회원 행을 먼저 잠근다.
        User user = userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(this::invalidToken);

        // 인증 기록을 다시 조회하면서 잠근다.
        PasswordResetToken record = tokenRepository
                .findByUserIdForUpdate(userId)
                .orElseThrow(this::invalidToken);

        LocalDateTime now = LocalDateTime.now(KST);

        // 재발급 및 동시 사용 여부까지 다시 검증한다.
        if (!record.isVerified()
                || record.isUsed()
                || record.isResetTokenExpired(now)
                || !matchesHash(
                record.getResetTokenHash(),
                tokenHash
        )) {

            throw invalidToken();
        }

        if (user.getPasswordHash() == null) {
            throw invalidToken();
        }

        // 기존 비밀번호와 동일한 비밀번호 사용 제한
        if (passwordEncoder.matches(
                request.newPassword(),
                user.getPasswordHash()
        )) {
            throw new IllegalArgumentException(
                    "기존 비밀번호와 다른 비밀번호를 입력해주세요."
            );
        }

        // 새 비밀번호 암호화 및 저장
        String encodedPassword =
                passwordEncoder.encode(request.newPassword());

        user.changePassword(encodedPassword);

        // 기존 로그인 토큰 무효화
        user.clearRefreshTokenHash();
        user.increaseTokenVersion();

        // 로그인 실패 횟수 및 계정 잠금 초기화
        user.resetLoginFailures();

        // 재설정 토큰 재사용 방지
        record.markUsed(now);
    }

    /* ==================== 인증번호 및 토큰 생성 ==================== */

    /**
     * 0으로 시작하는 값도 포함하여 숫자 6자리 인증번호를 생성한다.
     */
    private String generateCode() {
        return String.format(
                Locale.ROOT,
                "%06d",
                RANDOM.nextInt(1_000_000)
        );
    }

    /**
     * 256비트 난수를 이용해 URL-safe 재설정 토큰을 생성한다.
     */
    private String generateResetToken() {
        byte[] bytes = new byte[32];

        RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /* ==================== 해시 처리 ==================== */

    /**
     * 회원 ID를 포함하여 인증번호를 HMAC-SHA256으로 보호한다.
     */
    private String hashCode(
            Long userId,
            String code
    ) {
        return hmac(
                "password-reset-code:"
                        + userId + ":" + code
        );
    }

    /**
     * 재설정 토큰의 HMAC-SHA256 해시를 생성한다.
     */
    private String hashResetToken(String token) {
        return hmac(
                "password-reset-token:" + token
        );
    }

    /**
     * 서버 비밀키를 이용하여 HMAC-SHA256 해시를 생성한다.
     */
    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKeySpec key = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );

            mac.init(key);

            byte[] result = mac.doFinal(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(result);

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "인증 정보 해시 처리에 실패했습니다.",
                    e
            );
        }
    }

    /**
     * 해시 비교 시 일정 시간 비교 방식을 사용한다.
     */
    private boolean matchesHash(
            String storedHash,
            String requestedHash
    ) {
        if (storedHash == null || requestedHash == null) {
            return false;
        }

        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                requestedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    /* ==================== 입력값 및 설정 검증 ==================== */

    /**
     * 기존 비밀번호 길이 정책과 BCrypt 입력 제한을 검증한다.
     */
    private void validateNewPassword(String password) {
        if (password == null
                || password.isBlank()
                || password.length() < 8
                || password.length() > 64) {

            throw new IllegalArgumentException(
                    "비밀번호는 8~64자여야 합니다."
            );
        }

        if (password.getBytes(StandardCharsets.UTF_8).length
                > 72) {

            throw new IllegalArgumentException(
                    "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
            );
        }
    }

    /**
     * 비밀번호 재설정 기능의 서버 설정값을 검증한다.
     */
    private void validateConfiguration() {
        if (secret == null
                || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length
                < 32
                || codeExpirationSeconds <= 0
                || tokenExpirationSeconds <= 0
                || maxAttempts <= 0
                || resendIntervalSeconds <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "현재 비밀번호 찾기 기능을 이용할 수 없습니다."
            );
        }
    }

    /* ==================== 예외 처리 ==================== */

    private InvalidResetCodeException invalidCode() {
        return new InvalidResetCodeException();
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                INVALID_TOKEN_MESSAGE
        );
    }

    /**
     * 인증 실패 횟수를 유지하기 위한 인증번호 검증 전용 예외.
     */
    private static class InvalidResetCodeException
            extends ResponseStatusException {

        private InvalidResetCodeException() {
            super(
                    HttpStatus.BAD_REQUEST,
                    INVALID_CODE_MESSAGE
            );
        }
    }
}