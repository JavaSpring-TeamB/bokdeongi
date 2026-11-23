package com.bokdeongi.backend.auth.service;

import com.bokdeongi.backend.auth.dto.request.ConsentRequest;
import com.bokdeongi.backend.auth.dto.request.ConsentUpdateRequest;
import com.bokdeongi.backend.auth.dto.request.KakaoSignupRequest;
import com.bokdeongi.backend.auth.dto.request.LoginRequest;
import com.bokdeongi.backend.auth.dto.request.SignupRequest;
import com.bokdeongi.backend.auth.dto.response.LoginResponse;
import com.bokdeongi.backend.auth.dto.response.LoginResult;
import com.bokdeongi.backend.auth.dto.response.SignupResponse;
import com.bokdeongi.backend.auth.dto.response.ConsentStatusResponse;
import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.entity.UserConsent;
import com.bokdeongi.backend.auth.jwt.JwtTokenProvider;
import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import com.bokdeongi.backend.auth.repository.UserRepository;
import com.bokdeongi.backend.benefit.application.DocumentRecordRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.bokdeongi.backend.common.exception.AccountLockedException;
import com.bokdeongi.backend.auth.dto.request.PasswordChangeRequest;
import com.bokdeongi.backend.eligibility.service.ProfileChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 회원 인증 및 계정 관리 서비스.
 *
 * 일반/카카오 회원가입, 로그인, JWT 발급 및 재발급,
 * 약관 동의 변경, 로그아웃, 회원 탈퇴를 처리한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Set<String> ALLOWED_CONSENTS = Set.of(
            "TERMS", "PRIVACY", "OPTIONAL_INFO", "SENSITIVE"
    );

    private static final Set<String> REQUIRED_CONSENTS = Set.of(
            "TERMS", "PRIVACY"
    );

    private static final Set<String> CHANGEABLE_CONSENTS = Set.of(
            "OPTIONAL_INFO", "SENSITIVE", "DOC_RECORD"
    );

    // 카카오 임시 가입 정보 유효기간: 10분
    private static final long KAKAO_SIGNUP_TIMEOUT = 10 * 60 * 1000L;

    // 기존 Access Token 응답 유효기간: 1800초
    private static final long ACCESS_TOKEN_EXPIRES_IN = 1800L;

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;
    private final DocumentRecordRepository documentRecordRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.consent.terms-version}")
    private String termsConsentVersion;

    @Value("${app.consent.privacy-version}")
    private String privacyConsentVersion;

    @Value("${app.consent.sensitive-version}")
    private String sensitiveConsentVersion;

    @Value("${app.consent.optional-info-version}")
    private String optionalInfoConsentVersion;

    @Value("${app.consent.doc-record-version}")
    private String docRecordConsentVersion;

    /* ==================== 일반 회원가입 ==================== */

    /**
     * 이메일 회원가입.
     *
     * 이메일 중복과 약관 동의를 검증한 뒤,
     * 비밀번호를 암호화하여 회원 및 약관 이력을 저장한다.
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {

        // 1. 이메일 중복 확인
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 2. 약관 동의 검증
        validateConsents(request.consents(), false);

        // 3. 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        // 4. 회원 객체 생성
        User user = new User(
                request.email(),
                encodedPassword,
                request.name(),
                request.phoneNumber(),
                request.birthDate()
        );

        // 5. 회원 저장
        User savedUser;

        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시 가입 요청 등으로 DB UNIQUE 제약 조건에 위반된 경우
            throw new IllegalArgumentException(
                    "회원가입 정보를 확인해주세요.", e
            );
        }

        // 6. 약관 동의 이력 저장
        saveConsents(savedUser, request.consents());

        return new SignupResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                "회원가입이 완료되었습니다."
        );
    }

    /**
     * 회원가입 시 이메일 사용 가능 여부를 확인한다.
     *
     * @param email 확인할 이메일 주소
     * @return true: 사용 가능, false: 이미 가입된 이메일
     */
    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    /* ==================== 카카오 회원가입 ==================== */

    /**
     * 카카오 신규 회원가입.
     *
     * OAuth2 인증 과정에서 세션에 저장한 카카오 정보를 확인하고,
     * 약관 동의 완료 후 회원을 생성한다.
     */
    @Transactional
    public SignupResponse kakaoSignup(
            KakaoSignupRequest request,
            HttpServletRequest httpRequest
    ) {
        // 1. 카카오 인증 세션 확인
        HttpSession session = httpRequest.getSession(false);

        if (session == null) {
            throw new IllegalArgumentException("카카오 인증 정보가 없습니다.");
        }

        String kakaoId =
                (String) session.getAttribute("KAKAO_SIGNUP_ID");

        Long createdAt =
                (Long) session.getAttribute("KAKAO_SIGNUP_CREATED_AT");

        String nickname =
                (String) session.getAttribute("KAKAO_SIGNUP_NICKNAME");

        String email =
                (String) session.getAttribute("KAKAO_SIGNUP_EMAIL");

        if (kakaoId == null || createdAt == null) {
            throw new IllegalArgumentException("카카오 인증 정보가 없습니다.");
        }

        // 2. 임시 가입 정보 유효기간 확인
        long elapsed = System.currentTimeMillis() - createdAt;

        if (elapsed < 0 || elapsed > KAKAO_SIGNUP_TIMEOUT) {
            clearKakaoSignupSession(session);

            throw new IllegalArgumentException(
                    "카카오 인증 시간이 만료되었습니다."
            );
        }

        // 3. 약관 동의 및 문서 버전 검증
        validateConsents(request.consents(), true);

        for (ConsentRequest consent : request.consents()) {
            validateConsentVersion(
                    consent.consentType(),
                    consent.docVersion()
            );
        }

        // 4. 기존 카카오 회원 여부 확인
        if (userRepository.findByKakaoId(kakaoId).isPresent()) {
            throw new IllegalArgumentException(
                    "이미 가입된 카카오 계정입니다."
            );
        }

        // 5. 기존 이메일 계정과 중복되는지 확인
        if (email != null && !email.isBlank()
                && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "이미 가입된 이메일입니다. 기존 계정으로 로그인해 주세요."
            );
        }

        // 6. 카카오 회원 생성 및 저장
        User user = User.createKakaoUser(kakaoId, nickname, email);
        User savedUser;

        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시 가입 요청 등으로 DB 제약 조건에 위반된 경우
            throw new IllegalArgumentException(
                    "회원가입 정보를 확인해주세요.", e
            );
        }

        // 7. 약관 동의 이력 저장
        saveConsents(savedUser, request.consents());

        // 8. DB 트랜잭션 커밋 성공 후 임시 가입 정보 제거
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        clearKakaoSignupSession(session);
                    }
                }
        );

        return new SignupResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                "카카오 회원가입이 완료되었습니다."
        );
    }

    /* ==================== 로그인 ==================== */

    /**
     * 이메일과 비밀번호를 검증한다.
     *
     * 이메일 또는 비밀번호가 일치하지 않으면
     * 동일한 인증 실패 메시지를 반환한다.
     */
    @Transactional(readOnly = true)
    public User authenticate(String email, String password) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BadCredentialsException(
                                "이메일 또는 비밀번호가 올바르지 않습니다."
                        )
                );

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(
                password,
                user.getPasswordHash()
        )) {
            throw new BadCredentialsException(
                    "이메일 또는 비밀번호가 올바르지 않습니다."
            );
        }

        return user;
    }

    /**
     * 일반 이메일 로그인.
     *
     * 로그인 실패 횟수와 계정 잠금 상태를 확인한 후
     * 인증에 성공하면 Access Token과 Refresh Token을 발급한다.
     */
    @Transactional
    public LoginResult login(LoginRequest request) {

        // 1. 별도 트랜잭션에서 로그인 검증 및 실패 기록 처리
        LoginAttemptService.LoginAttemptResult result =
                loginAttemptService.attempt(
                        request.email(),
                        request.password()
                );

        // 2. 계정이 잠긴 경우
        if (result.status()
                == LoginAttemptService.LoginStatus.LOCKED) {
            throw new AccountLockedException(
                    "로그인 5회 실패로 계정이 잠겼습니다. 1분 후 다시 시도해주세요."
            );
        }

        // 3. 이메일 또는 비밀번호가 올바르지 않은 경우
        if (result.status()
                != LoginAttemptService.LoginStatus.SUCCESS) {
            throw new BadCredentialsException(
                    "이메일 또는 비밀번호가 올바르지 않습니다."
            );
        }

        // 4. 인증 성공 시 기존 JWT 발급 로직 사용
        return issueTokens(result.userId());
    }

    /**
     * 회원 ID를 이용하여 JWT를 발급한다.
     *
     * 일반 로그인과 카카오 로그인에서 사용하며,
     * Refresh Token 해시 및 마지막 로그인 시각을 DB에 저장한다.
     */
    @Transactional
    public LoginResult issueTokens(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 회원입니다."
                        )
                );

        user.updateLastLoginAt(
                LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        );

        return createLoginResult(user);
    }


    /* ==================== 비밀번호 변경 ==================== */

    /**
     * 로그인한 이메일 회원의 비밀번호를 변경한다.
     *
     * 현재 비밀번호 검증 후 새 비밀번호를 암호화하여 저장한다.
     * 변경 성공 시 기존 인증 토큰을 무효화하도록 처리한다.
     */
    @Transactional
    public void changePassword(
            Long userId,
            PasswordChangeRequest request
    ) {
        // 1. 회원 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BadCredentialsException(
                                "사용자를 찾을 수 없습니다."
                        )
                );

        // 2. 카카오 전용 계정 등 비밀번호가 없는 회원 제한
        if (user.getPasswordHash() == null) {
            throw new IllegalArgumentException(
                    "비밀번호 변경을 지원하지 않는 계정입니다."
            );
        }

        // 3. 현재 비밀번호 검증
        if (!passwordEncoder.matches(
                request.currentPassword(),
                user.getPasswordHash()
        )) {
            throw new BadCredentialsException(
                    "현재 비밀번호가 올바르지 않습니다."
            );
        }

        // 4. 기존 비밀번호와 새 비밀번호 동일 여부 확인
        if (passwordEncoder.matches(
                request.newPassword(),
                user.getPasswordHash()
        )) {
            throw new IllegalArgumentException(
                    "기존 비밀번호와 다른 비밀번호를 입력해주세요."
            );
        }

        // 5. 새 비밀번호 암호화
        String encodedPassword =
                passwordEncoder.encode(request.newPassword());

        // 6. 비밀번호 변경
        user.changePassword(encodedPassword);

        // 7. 기존 Refresh Token 무효화
        user.clearRefreshTokenHash();

        // 8. 토큰 버전 증가
        user.increaseTokenVersion();
    }

    /* ==================== 약관 동의 변경 ==================== */

    /**
     * 선택 약관 동의 변경 및 철회.
     *
     * OPTIONAL_INFO와 SENSITIVE만 변경할 수 있으며,
     * 기존 이력을 수정하지 않고 새로운 기록을 추가한다.
     */
    @Transactional
    public void updateConsent(
            Long userId,
            ConsentUpdateRequest request
    ) {
        // 1. 변경 가능한 약관 유형 확인
        if (!CHANGEABLE_CONSENTS.contains(request.consentType())) {
            throw new IllegalArgumentException(
                    "변경할 수 없는 동의 항목입니다."
            );
        }

        // 서버에 설정된 동의 문서 버전과 요청 버전 비교
        validateConsentVersion(
                request.consentType(),
                request.docVersion()
        );

        // 2. 회원 조회 및 동의 변경을 위한 쓰기 잠금
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 회원입니다."
                        )
                );

        // 3. 해당 약관의 최신 동의 이력 조회
        UserConsent latestConsent = userConsentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        userId,
                        request.consentType()
                )
                .orElse(null);

        // 민감정보 동의 철회 시 기존 데이터 삭제 및 재매칭 요청
        if ("SENSITIVE".equals(request.consentType())
                && Boolean.FALSE.equals(request.agreed())
                && user.getSensitiveEnc() != null) {

            user.clearSensitiveInfo();

            eventPublisher.publishEvent(
                    new ProfileChangedEvent(userId)
            );
        }

        // DOC_RECORD 동의 철회 시 저장된 서류 기록 삭제
        if ("DOC_RECORD".equals(request.consentType())
                && Boolean.FALSE.equals(request.agreed())) {

            documentRecordRepository.deleteRecords(userId);
        }

        // 4. 동의 여부와 문서 버전이 모두 동일하면 저장하지 않음
        if (latestConsent != null
                && latestConsent.getAgreed().equals(request.agreed())
                && latestConsent.getDocVersion().equals(request.docVersion())) {
            return;
        }

        // 5. 변경된 동의 상태를 새 이력으로 저장
        userConsentRepository.save(
                new UserConsent(
                        user,
                        request.consentType(),
                        request.docVersion(),
                        request.agreed()
                )
        );
    }

    /**
     * 회원의 최신 동의 상태를 조회한다.
     *
     * 동의 이력이 없는 경우 agreed, docVersion, consentId는 null이다.
     *
     * @param userId 회원 ID
     * @return 약관 유형별 최신 동의 상태
     */
    @Transactional(readOnly = true)
    public ConsentStatusResponse getConsentStatus(Long userId) {

        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("존재하지 않는 회원입니다.");
        }

        List<ConsentStatusResponse.ConsentItem> consents =
                List.of("OPTIONAL_INFO", "SENSITIVE", "DOC_RECORD")
                        .stream()
                        .map(consentType -> userConsentRepository
                                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                                        userId,
                                        consentType
                                )
                                .map(consent -> new ConsentStatusResponse.ConsentItem(
                                        consentType,
                                        consent.getAgreed(),
                                        consent.getDocVersion(),
                                        consent.getId()
                                ))
                                .orElseGet(() -> new ConsentStatusResponse.ConsentItem(
                                        consentType,
                                        null,
                                        null,
                                        null
                                )))
                        .toList();

        return new ConsentStatusResponse(consents);
    }

    /**
     * 동의 유형에 해당하는 서버 문서 버전을 검증한다.
     *
     * @param consentType 동의 유형
     * @param docVersion 요청 문서 버전
     */
    private void validateConsentVersion(
            String consentType,
            String docVersion
    ) {
        String expectedVersion = switch (consentType) {
            case "TERMS" -> termsConsentVersion;
            case "PRIVACY" -> privacyConsentVersion;
            case "OPTIONAL_INFO" -> optionalInfoConsentVersion;
            case "SENSITIVE" -> sensitiveConsentVersion;
            case "DOC_RECORD" -> docRecordConsentVersion;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 동의 유형입니다."
            );
        };

        if (!expectedVersion.equals(docVersion)) {
            throw new IllegalArgumentException(
                    "현재 동의 문서 버전과 일치하지 않습니다."
            );
        }
    }

    /* ==================== 토큰 재발급 ==================== */

    /**
     * Refresh Token을 검증하고 새 JWT를 발급한다.
     *
     * JWT 유효성, 저장된 해시값, 토큰 버전을 확인한 뒤
     * Refresh Token을 회전시킨다.
     */
    @Transactional
    public LoginResult refresh(String refreshToken) {

        // 1. Refresh Token 유효성 검증
        if (refreshToken == null
                || !jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new BadCredentialsException(
                    "유효하지 않은 Refresh Token입니다."
            );
        }

        // 2. JWT에서 회원 ID 추출 후 조회
        Long userId = jwtTokenProvider.getUserId(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BadCredentialsException(
                                "사용자를 찾을 수 없습니다."
                        )
                );

        // 3. DB에 저장된 Refresh Token 해시와 비교
        String savedHash = user.getRefreshTokenHash();
        String receivedHash = hashRefreshToken(refreshToken);

        if (savedHash == null
                || !MessageDigest.isEqual(
                savedHash.getBytes(StandardCharsets.UTF_8),
                receivedHash.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new BadCredentialsException(
                    "Refresh Token이 일치하지 않습니다."
            );
        }

        // 4. JWT의 토큰 버전과 DB의 토큰 버전 비교
        Integer tokenVersion =
                jwtTokenProvider.getTokenVersion(refreshToken);

        if (tokenVersion == null
                || !tokenVersion.equals(user.getTokenVersion())) {
            throw new BadCredentialsException(
                    "무효화된 Refresh Token입니다."
            );
        }

        // 5. 새로운 JWT 발급 및 Refresh Token 해시 갱신
        return createLoginResult(user);
    }

    /* ==================== 로그아웃 ==================== */

    /**
     * 로그아웃 처리.
     *
     * Refresh Token 해시를 제거하고 토큰 버전을 증가시켜
     * 기존 Access Token과 Refresh Token을 무효화한다.
     */
    @Transactional
    public void logout(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BadCredentialsException(
                                "사용자를 찾을 수 없습니다."
                        )
                );

        user.clearRefreshTokenHash();
        user.increaseTokenVersion();
    }

    /* ==================== 회원 탈퇴 ==================== */

    /**
     * 회원 탈퇴 및 개인정보 삭제.
     *
     * 회원을 DB에서 삭제하며,
     * 관련 데이터는 DB에 설정된 ON DELETE CASCADE로 삭제한다.
     */
    @Transactional
    public void withdraw(Long userId) {

        // 1. 탈퇴할 회원 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 회원입니다."
                        )
                );

        // 2. 회원 삭제
        userRepository.delete(user);

        // 3. 삭제 SQL 실행
        userRepository.flush();
    }

    /* ==================== 공통 메서드 ==================== */

    /**
     * 회원가입 약관 동의 검증.
     *
     * 허용된 약관 유형인지 확인하고,
     * 중복 항목 및 필수 약관 동의 여부를 검사한다.
     *
     * 기존 일반/카카오 회원가입의 예외 메시지를 구분하여 유지한다.
     */
    private void validateConsents(
            List<ConsentRequest> consents,
            boolean kakaoSignup
    ) {
        Set<String> receivedTypes = new HashSet<>();

        // 허용되지 않은 유형 및 중복 검사
        for (ConsentRequest consent : consents) {

            if (!ALLOWED_CONSENTS.contains(consent.consentType())) {
                if (kakaoSignup) {
                    throw new IllegalArgumentException(
                            "허용되지 않은 동의 항목입니다."
                    );
                }

                throw new IllegalArgumentException(
                        "유효하지 않은 약관 유형입니다: "
                                + consent.consentType()
                );
            }

            if (!receivedTypes.add(consent.consentType())) {
                throw new IllegalArgumentException(
                        kakaoSignup
                                ? "중복된 동의 항목입니다."
                                : "중복된 약관 동의 항목입니다."
                );
            }
        }

        // 필수 약관 동의 여부 확인
        for (String requiredType : REQUIRED_CONSENTS) {
            boolean agreed = consents.stream()
                    .anyMatch(consent ->
                            requiredType.equals(consent.consentType())
                                    && Boolean.TRUE.equals(consent.agreed())
                    );

            if (!agreed) {
                throw new IllegalArgumentException(
                        kakaoSignup
                                ? "필수 약관에 동의해야 합니다."
                                : "필수 약관에 동의해야 합니다: " + requiredType
                );
            }
        }
    }

    /**
     * 회원가입 시 전달받은 약관 동의 목록을 이력으로 저장한다.
     */
    private void saveConsents(
            User user,
            List<ConsentRequest> consents
    ) {
        for (ConsentRequest consent : consents) {
            userConsentRepository.save(
                    new UserConsent(
                            user,
                            consent.consentType(),
                            consent.docVersion(),
                            consent.agreed()
                    )
            );
        }
    }

    /**
     * Access Token과 Refresh Token을 생성하고,
     * Refresh Token 해시를 회원 정보에 저장한다.
     *
     * 로그인과 토큰 재발급에서 공통으로 사용한다.
     */
    private LoginResult createLoginResult(User user) {

        String accessToken =
                jwtTokenProvider.generateAccessToken(user);

        String refreshToken =
                jwtTokenProvider.generateRefreshToken(user);

        // 원본 Refresh Token 대신 SHA-256 해시 저장
        user.updateRefreshTokenHash(hashRefreshToken(refreshToken));

        LoginResponse loginResponse = new LoginResponse(
                accessToken,
                "Bearer",
                ACCESS_TOKEN_EXPIRES_IN
        );

        return new LoginResult(loginResponse, refreshToken);
    }

    /**
     * Refresh Token을 SHA-256으로 해싱한다.
     *
     * DB에는 원본 토큰이 아닌 해시값을 저장하여
     * Refresh Token 유출 위험을 줄인다.
     */
    private String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    refreshToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 해시 알고리즘을 사용할 수 없습니다.", e
            );
        }
    }

    /**
     * 카카오 회원가입 임시 세션 정보를 제거한다.
     *
     * 인증 정보가 만료되었거나
     * 회원가입 트랜잭션이 성공적으로 커밋된 후 호출한다.
     */
    private void clearKakaoSignupSession(HttpSession session) {
        session.removeAttribute("KAKAO_SIGNUP_ID");
        session.removeAttribute("KAKAO_SIGNUP_CREATED_AT");
        session.removeAttribute("KAKAO_SIGNUP_NICKNAME");
        session.removeAttribute("KAKAO_SIGNUP_EMAIL");
    }
}