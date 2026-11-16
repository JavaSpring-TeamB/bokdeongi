package com.bokdeongi.backend.auth.service;

import com.bokdeongi.backend.auth.dto.request.ConsentRequest;
import com.bokdeongi.backend.auth.dto.request.SignupRequest;
import com.bokdeongi.backend.auth.dto.response.SignupResponse;
import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.entity.UserConsent;
import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import com.bokdeongi.backend.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bokdeongi.backend.auth.dto.request.LoginRequest;
import com.bokdeongi.backend.auth.dto.response.LoginResponse;
import com.bokdeongi.backend.auth.jwt.JwtTokenProvider;
import com.bokdeongi.backend.auth.dto.request.KakaoSignupRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Set;
import java.util.HashSet;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.bokdeongi.backend.auth.dto.request.ConsentUpdateRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.bokdeongi.backend.auth.dto.response.LoginResult;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /* 회원가입 */
    @Transactional
    public SignupResponse signup(SignupRequest request) {

        // 1. 이메일 중복 확인
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 2. 약관 유형 및 중복 확인
        Set<String> allowedConsents = Set.of(
                "TERMS",
                "PRIVACY",
                "OPTIONAL_INFO",
                "SENSITIVE"
        );

        Set<String> consentTypes = new HashSet<>();

        for (ConsentRequest consent : request.consents()) {
            if (!allowedConsents.contains(consent.consentType())) {
                throw new IllegalArgumentException(
                        "유효하지 않은 약관 유형입니다: " + consent.consentType()
                );
            }

            if (!consentTypes.add(consent.consentType())) {
                throw new IllegalArgumentException(
                        "중복된 약관 동의 항목입니다."
                );
            }
        }
        
        // 3. 필수 약관 동의 검증
        Set<String> requiredConsents = Set.of(
                "TERMS",
                "PRIVACY"
        );

        for (String requiredType : requiredConsents) {
            boolean agreed = request.consents().stream()
                    .anyMatch(consent ->
                            requiredType.equals(consent.consentType())
                                    && consent.agreed()
                    );

            if (!agreed) {
                throw new IllegalArgumentException(
                        "필수 약관에 동의해야 합니다: " + requiredType
                );
            }
        }

        // 4. 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        // 5. 사용자 생성
        User user = new User(
                request.email(),
                encodedPassword,
                request.name(),
                request.phoneNumber(),
                request.birthDate()
        );

        // 6. 사용자 저장
        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시 가입 요청에 대한 DB UNIQUE 제약 보호
            throw new IllegalArgumentException("회원가입 정보를 확인해주세요.", e);
        }

        // 7. 약관 동의 이력 저장
        for (ConsentRequest consent : request.consents()) {
            UserConsent userConsent = new UserConsent(
                    savedUser,
                    consent.consentType(),
                    consent.docVersion(),
                    consent.agreed()
            );

            userConsentRepository.save(userConsent);
        }

        // 8. 회원가입 결과 반환
        return new SignupResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                "회원가입이 완료되었습니다."
        );
    }

    @Transactional
    public SignupResponse kakaoSignup(
            KakaoSignupRequest request,
            HttpServletRequest httpRequest
    ) {
        // 1. 카카오 인증 후 생성된 임시 세션 확인
        HttpSession session = httpRequest.getSession(false);

        if (session == null) {
            throw new IllegalArgumentException("카카오 인증 정보가 없습니다.");
        }

        String kakaoId = (String) session.getAttribute("KAKAO_SIGNUP_ID");
        Long createdAt = (Long) session.getAttribute("KAKAO_SIGNUP_CREATED_AT");
        String nickname = (String) session.getAttribute("KAKAO_SIGNUP_NICKNAME");
        String email = (String) session.getAttribute("KAKAO_SIGNUP_EMAIL");

        if (kakaoId == null || createdAt == null) {
            throw new IllegalArgumentException("카카오 인증 정보가 없습니다.");
        }

        // 2. 임시 가입 정보 유효기간 10분
        long elapsed = System.currentTimeMillis() - createdAt;

        if (elapsed < 0 || elapsed > 10 * 60 * 1000L) {
            session.removeAttribute("KAKAO_SIGNUP_ID");
            session.removeAttribute("KAKAO_SIGNUP_CREATED_AT");
            session.removeAttribute("KAKAO_SIGNUP_NICKNAME");
            session.removeAttribute("KAKAO_SIGNUP_EMAIL");
            throw new IllegalArgumentException("카카오 인증 시간이 만료되었습니다.");
        }

        // 3. 필수 약관 및 중복 동의 검증
        Set<String> allowedTypes = Set.of(
                "TERMS", "PRIVACY", "OPTIONAL_INFO", "SENSITIVE"
        );

        Set<String> receivedTypes = new HashSet<>();

        for (var consent : request.consents()) {
            if (!allowedTypes.contains(consent.consentType())) {
                throw new IllegalArgumentException("허용되지 않은 동의 항목입니다.");
            }

            if (!receivedTypes.add(consent.consentType())) {
                throw new IllegalArgumentException("중복된 동의 항목입니다.");
            }
        }

        for (String requiredType : Set.of("TERMS", "PRIVACY")) {
            boolean agreed = request.consents().stream()
                    .anyMatch(consent ->
                            requiredType.equals(consent.consentType())
                                    && Boolean.TRUE.equals(consent.agreed())
                    );

            if (!agreed) {
                throw new IllegalArgumentException("필수 약관에 동의해야 합니다.");
            }
        }

        // 4. 이미 가입된 카카오 회원인지 확인
        if (userRepository.findByKakaoId(kakaoId).isPresent()) {
            throw new IllegalArgumentException("이미 가입된 카카오 계정입니다.");
        }

        // 5. 카카오 회원 생성
        User user = User.createKakaoUser(kakaoId, nickname, email);
        User savedUser = userRepository.saveAndFlush(user);

        // 6. 동의 이력 저장
        for (var consent : request.consents()) {
            userConsentRepository.save(
                    new UserConsent(
                            savedUser,
                            consent.consentType(),
                            consent.docVersion(),
                            consent.agreed()
                    )
            );
        }

        // 7. DB 트랜잭션 커밋 성공 후 임시 가입 정보 제거
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        session.removeAttribute("KAKAO_SIGNUP_ID");
                        session.removeAttribute("KAKAO_SIGNUP_CREATED_AT");
                        session.removeAttribute("KAKAO_SIGNUP_NICKNAME");
                        session.removeAttribute("KAKAO_SIGNUP_EMAIL");
                    }
                }
        );

        return new SignupResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                "카카오 회원가입이 완료되었습니다."
        );
    }

    /* 로그인 */
    @Transactional(readOnly = true)
    public User authenticate(String email, String password) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BadCredentialsException(
                                "이메일 또는 비밀번호가 올바르지 않습니다."
                        )
                );

        if (user.getPasswordHash() == null ||
                !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException(
                    "이메일 또는 비밀번호가 올바르지 않습니다."
            );
        }

        return user;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {

        // 1. 이메일로 사용자 조회
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() ->
                        new BadCredentialsException(
                                "이메일 또는 비밀번호가 올바르지 않습니다."
                        )
                );

        // 2. 비밀번호 검증
        if (user.getPasswordHash() == null ||
                !passwordEncoder.matches(
                        request.password(),
                        user.getPasswordHash()
                )) {
            throw new BadCredentialsException(
                    "이메일 또는 비밀번호가 올바르지 않습니다."
            );
        }

        // 3. 공통 토큰 발급 메서드 호출
        return issueTokens(user.getId());
    }

    @Transactional
    public LoginResult issueTokens(Long userId) {

        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 회원입니다.")
                );

        // 2. Access Token 발급
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        // 3. Refresh Token 발급
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        // 4. Refresh Token 해시 저장
        user.updateRefreshTokenHash(hashRefreshToken(refreshToken));

        // 5. 로그인 결과 반환
        LoginResponse loginResponse = new LoginResponse(
                accessToken,
                "Bearer",
                1800
        );

        return new LoginResult(loginResponse, refreshToken);
    }

    /* 선택 동의 변경 및 철회 */
    @Transactional
    public void updateConsent(
            Long userId,
            ConsentUpdateRequest request
    ) {
        // 1. 변경 가능한 동의 유형 확인
        Set<String> allowedTypes = Set.of(
                "OPTIONAL_INFO",
                "SENSITIVE"
        );

        if (!allowedTypes.contains(request.consentType())) {
            throw new IllegalArgumentException(
                    "변경할 수 없는 동의 항목입니다."
            );
        }

        // 2. 회원 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 회원입니다.")
                );

        // 3. 최신 동의 상태 조회
        UserConsent latestConsent = userConsentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        userId,
                        request.consentType()
                )
                .orElse(null);

        // 4. 이전 동의 상태와 동일한지 확인
        if (latestConsent != null
                && latestConsent.getAgreed().equals(request.agreed())
                && latestConsent.getDocVersion().equals(request.docVersion())) {
            return;
        }

        // 5. 기존 기록을 수정하지 않고 새로운 이력 저장
        userConsentRepository.save(
                new UserConsent(
                        user,
                        request.consentType(),
                        request.docVersion(),
                        request.agreed()
                )
        );
    }

    private String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

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

    @Transactional
    public LoginResult refresh(String refreshToken) {

        // 1. Refresh Token 유효성 검사
        if (refreshToken == null ||
                !jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new BadCredentialsException("유효하지 않은 Refresh Token입니다.");
        }

        // 2. 사용자 조회
        Long userId = jwtTokenProvider.getUserId(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BadCredentialsException("사용자를 찾을 수 없습니다.")
                );

        // 3. DB에 저장된 해시값과 비교
        String savedHash = user.getRefreshTokenHash();
        String receivedHash = hashRefreshToken(refreshToken);

        if (savedHash == null ||
                !MessageDigest.isEqual(
                        savedHash.getBytes(StandardCharsets.UTF_8),
                        receivedHash.getBytes(StandardCharsets.UTF_8)
                )) {
            throw new BadCredentialsException("Refresh Token이 일치하지 않습니다.");
        }

        // 4. tokenVersion 검증
        Integer tokenVersion = jwtTokenProvider.getTokenVersion(refreshToken);

        if (tokenVersion == null ||
                !tokenVersion.equals(user.getTokenVersion())) {
            throw new BadCredentialsException("무효화된 Refresh Token입니다.");
        }

        // 5. 새 토큰 발급
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);

        // 6. DB에 새 Refresh Token 해시 저장
        user.updateRefreshTokenHash(hashRefreshToken(newRefreshToken));

        // 7. 로그인 결과 반환
        LoginResponse loginResponse = new LoginResponse(
                newAccessToken,
                "Bearer",
                1800
        );

        return new LoginResult(loginResponse, newRefreshToken);
    }

    @Transactional
    public void logout(Long userId) {

        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BadCredentialsException("사용자를 찾을 수 없습니다.")
                );

        // 2. Refresh Token 무효화
        user.clearRefreshTokenHash();

        // 3. 기존 Access Token 무효화
        user.increaseTokenVersion();
    }
}