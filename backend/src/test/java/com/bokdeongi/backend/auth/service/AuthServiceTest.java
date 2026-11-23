package com.bokdeongi.backend.auth.service;

import com.bokdeongi.backend.auth.dto.request.ConsentUpdateRequest;
import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.entity.UserConsent;
import com.bokdeongi.backend.auth.jwt.JwtTokenProvider;
import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import com.bokdeongi.backend.auth.repository.UserRepository;
import com.bokdeongi.backend.eligibility.service.ProfileChangedEvent;
import com.bokdeongi.backend.benefit.application.DocumentRecordRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import com.bokdeongi.backend.auth.dto.request.ConsentRequest;
import com.bokdeongi.backend.auth.dto.request.KakaoSignupRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;

import com.bokdeongi.backend.auth.dto.response.SignupResponse;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private UserConsentRepository userConsentRepository;
    private ApplicationEventPublisher eventPublisher;
    private AuthService authService;
    private DocumentRecordRepository documentRecordRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userConsentRepository = mock(UserConsentRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        documentRecordRepository = mock(DocumentRecordRepository.class);

        authService = new AuthService(
                userRepository,
                userConsentRepository,
                documentRecordRepository,
                mock(PasswordEncoder.class),
                mock(JwtTokenProvider.class),
                mock(LoginAttemptService.class),
                eventPublisher
        );

        // Mockito로 직접 생성한 서비스에는
        // Spring의 @Value가 자동 주입되지 않는다.
        ReflectionTestUtils.setField(
                authService,
                "sensitiveConsentVersion",
                "v1.0"
        );

        ReflectionTestUtils.setField(
                authService,
                "optionalInfoConsentVersion",
                "v1.0"
        );

        ReflectionTestUtils.setField(
                authService,
                "termsConsentVersion",
                "v1.0"
        );

        ReflectionTestUtils.setField(
                authService,
                "privacyConsentVersion",
                "v1.0"
        );

        ReflectionTestUtils.setField(
                authService,
                "docRecordConsentVersion",
                "v1.0"
        );
    }

    /**
     * AUTH-03: 민감정보 동의를 철회하면
     * 기존 민감정보를 삭제하고 철회 이력을 저장한다.
     */
    @Test
    void shouldDeleteSensitiveInfoWhenConsentWithdrawn() {

        // Given: 민감정보가 저장된 사용자
        Long userId = 1L;

        User user = new User(
                "test@example.com",
                "passwordHash",
                "테스트",
                "01012345678",
                null
        );

        // 기존 민감정보 설정
        // 실제 DB 저장 시 JPA Converter에서 암호화한다.
        user.updateProfileStep4("[\"JA0301\"]");

        // 동의 변경 시 사용자 행 잠금 조회
        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        // 기존 민감정보 동의 상태
        UserConsent previousConsent = new UserConsent(
                user,
                "SENSITIVE",
                "v1.0",
                true
        );

        when(userConsentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        eq(userId),
                        eq("SENSITIVE")
                ))
                .thenReturn(Optional.of(previousConsent));

        ConsentUpdateRequest request = new ConsentUpdateRequest(
                "SENSITIVE",
                "v1.0",
                false
        );

        // When: 민감정보 동의 철회
        authService.updateConsent(userId, request);

        // Then: 기존 민감정보가 삭제되어야 한다.
        assertNull(user.getSensitiveEnc());

        // 철회 이력이 저장되어야 한다.
        verify(userConsentRepository).save(
                argThat(consent ->
                        "SENSITIVE".equals(consent.getConsentType())
                                && Boolean.FALSE.equals(consent.getAgreed())
                )
        );

        // 프로필 변경 이벤트가 발행되어야 한다.
        verify(eventPublisher).publishEvent(
                any(ProfileChangedEvent.class)
        );
    }

    /**
     * AUTH-03: 이미 민감정보 동의를 철회한 경우
     * 동일한 철회 요청으로 중복 이력을 생성하지 않는다.
     */
    @Test
    void shouldNotRepeatSensitiveConsentWithdrawal() {

        // Given: 이미 동의를 철회한 사용자
        Long userId = 1L;

        User user = new User(
                "test@example.com",
                "passwordHash",
                "테스트",
                "01012345678",
                null
        );

        // 동의 변경 시 사용자 행 잠금 조회
        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        // 최신 동의 이력이 이미 false인 상황
        UserConsent previousConsent = new UserConsent(
                user,
                "SENSITIVE",
                "v1.0",
                false
        );

        when(userConsentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        eq(userId),
                        eq("SENSITIVE")
                ))
                .thenReturn(Optional.of(previousConsent));

        ConsentUpdateRequest request = new ConsentUpdateRequest(
                "SENSITIVE",
                "v1.0",
                false
        );

        int originalVersion = user.getProfileVersion();

        // When: 동일한 동의 철회 요청
        authService.updateConsent(userId, request);

        // Then: 민감정보는 NULL 상태를 유지한다.
        assertNull(user.getSensitiveEnc());

        // 프로필 버전은 변경되지 않아야 한다.
        assertEquals(
                originalVersion,
                user.getProfileVersion()
        );

        // 중복 동의 이력을 저장하지 않아야 한다.
        verify(userConsentRepository, never())
                .save(any(UserConsent.class));

        // 불필요한 프로필 변경 이벤트가 발생하지 않아야 한다.
        verifyNoInteractions(eventPublisher);
    }


    @Test
    void shouldRejectKakaoSignupWhenTermsVersionMismatch() {
        // Given: 유효한 카카오 임시 가입 세션
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(httpRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("KAKAO_SIGNUP_ID"))
                .thenReturn("123456789");
        when(session.getAttribute("KAKAO_SIGNUP_CREATED_AT"))
                .thenReturn(System.currentTimeMillis());

        KakaoSignupRequest request = new KakaoSignupRequest(
                List.of(
                        new ConsentRequest("TERMS", "v0.9", true),
                        new ConsentRequest("PRIVACY", "v1.0", true)
                )
        );

        // When & Then: 약관 버전이 다르면 가입 거부
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.kakaoSignup(request, httpRequest)
        );

        assertEquals(
                "현재 동의 문서 버전과 일치하지 않습니다.",
                exception.getMessage()
        );

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldRejectKakaoSignupWhenPrivacyVersionMismatch() {
        // Given: 유효한 카카오 임시 가입 세션
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(httpRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("KAKAO_SIGNUP_ID"))
                .thenReturn("123456789");
        when(session.getAttribute("KAKAO_SIGNUP_CREATED_AT"))
                .thenReturn(System.currentTimeMillis());

        KakaoSignupRequest request = new KakaoSignupRequest(
                List.of(
                        new ConsentRequest("TERMS", "v1.0", true),
                        new ConsentRequest("PRIVACY", "v0.9", true)
                )
        );

        // When & Then: 개인정보 동의 버전이 다르면 가입 거부
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.kakaoSignup(request, httpRequest)
        );

        assertEquals(
                "현재 동의 문서 버전과 일치하지 않습니다.",
                exception.getMessage()
        );

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldSignupKakaoUserWhenConsentVersionsMatch() {
        // Given: 유효한 카카오 임시 가입 세션
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(httpRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("KAKAO_SIGNUP_ID"))
                .thenReturn("123456789");
        when(session.getAttribute("KAKAO_SIGNUP_CREATED_AT"))
                .thenReturn(System.currentTimeMillis());
        when(session.getAttribute("KAKAO_SIGNUP_NICKNAME"))
                .thenReturn("테스트");
        when(session.getAttribute("KAKAO_SIGNUP_EMAIL"))
                .thenReturn("kakao@example.com");

        when(userRepository.findByKakaoId("123456789"))
                .thenReturn(Optional.empty());

        when(userRepository.existsByEmail("kakao@example.com"))
                .thenReturn(false);

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KakaoSignupRequest request = new KakaoSignupRequest(
                List.of(
                        new ConsentRequest("TERMS", "v1.0", true),
                        new ConsentRequest("PRIVACY", "v1.0", true)
                )
        );

        // 실제 트랜잭션 없이 동기화 등록을 테스트하기 위한 설정
        TransactionSynchronizationManager.initSynchronization();

        try {
            // When: 정상 약관 버전으로 카카오 회원가입
            SignupResponse response = assertDoesNotThrow(
                    () -> authService.kakaoSignup(request, httpRequest)
            );

            // Then: 회원 저장 및 약관 이력 저장 확인
            verify(userRepository).saveAndFlush(any(User.class));
            verify(userConsentRepository, times(2))
                    .save(any(UserConsent.class));

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldRejectKakaoSignupWhenEmailAlreadyExists() {
        // Given: 유효한 카카오 임시 가입 세션
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(httpRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("KAKAO_SIGNUP_ID"))
                .thenReturn("123456789");
        when(session.getAttribute("KAKAO_SIGNUP_CREATED_AT"))
                .thenReturn(System.currentTimeMillis());
        when(session.getAttribute("KAKAO_SIGNUP_EMAIL"))
                .thenReturn("existing@example.com");

        // 기존 이메일 회원이 존재하는 상황
        when(userRepository.findByKakaoId("123456789"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("existing@example.com"))
                .thenReturn(true);

        KakaoSignupRequest request = new KakaoSignupRequest(
                List.of(
                        new ConsentRequest("TERMS", "v1.0", true),
                        new ConsentRequest("PRIVACY", "v1.0", true)
                )
        );

        // When: 동일 이메일로 카카오 회원가입 시도
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.kakaoSignup(request, httpRequest)
        );

        // Then: 중복 이메일 안내 및 회원 생성 차단
        assertEquals(
                "이미 가입된 이메일입니다. 기존 계정으로 로그인해 주세요.",
                exception.getMessage()
        );

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(userConsentRepository, never()).save(any(UserConsent.class));
    }

    @Test
    void shouldRejectKakaoSignupWhenDatabaseConstraintViolated() {
        // Given: 유효한 카카오 임시 가입 세션
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(httpRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("KAKAO_SIGNUP_ID"))
                .thenReturn("123456789");
        when(session.getAttribute("KAKAO_SIGNUP_CREATED_AT"))
                .thenReturn(System.currentTimeMillis());
        when(session.getAttribute("KAKAO_SIGNUP_EMAIL"))
                .thenReturn("kakao@example.com");

        // 사전 중복 검사에서는 중복이 발견되지 않음
        when(userRepository.findByKakaoId("123456789"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("kakao@example.com"))
                .thenReturn(false);

        // 실제 DB 저장 시 제약 조건 위반 발생
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry"
                ));

        KakaoSignupRequest request = new KakaoSignupRequest(
                List.of(
                        new ConsentRequest("TERMS", "v1.0", true),
                        new ConsentRequest("PRIVACY", "v1.0", true)
                )
        );

        // When: 카카오 회원가입 시도
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.kakaoSignup(request, httpRequest)
        );

        // Then: DB 오류를 회원가입 오류로 변환
        assertEquals(
                "회원가입 정보를 확인해주세요.",
                exception.getMessage()
        );

        verify(userRepository).saveAndFlush(any(User.class));

        // 회원 저장 실패 시 약관 이력은 저장하지 않음
        verify(userConsentRepository, never())
                .save(any(UserConsent.class));
    }


    @Test
    void shouldDeleteDocumentRecordsWhenConsentWithdrawn() {
        Long userId = 1L;

        User user = mock(User.class);

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        when(userConsentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        userId, "DOC_RECORD"
                ))
                .thenReturn(Optional.empty());

        ConsentUpdateRequest request =
                new ConsentUpdateRequest(
                        "DOC_RECORD",
                        "v1.0",
                        false
                );

        authService.updateConsent(userId, request);

        verify(documentRecordRepository)
                .deleteRecords(userId);

        verify(userConsentRepository)
                .save(argThat(consent ->
                        "DOC_RECORD".equals(consent.getConsentType())
                                && Boolean.FALSE.equals(consent.getAgreed())
                ));
    }

    @Test
    void shouldNotDeleteDocumentRecordsWhenConsentGranted() {
        Long userId = 1L;

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(mock(User.class)));

        when(userConsentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        userId, "DOC_RECORD"
                ))
                .thenReturn(Optional.empty());

        authService.updateConsent(
                userId,
                new ConsentUpdateRequest(
                        "DOC_RECORD", "v1.0", true
                )
        );

        verify(documentRecordRepository, never())
                .deleteRecords(any());
    }

    @Test
    void shouldNotDeleteDocumentRecordsForOtherConsent() {
        Long userId = 1L;

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(mock(User.class)));

        when(userConsentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        userId, "OPTIONAL_INFO"
                ))
                .thenReturn(Optional.empty());

        authService.updateConsent(
                userId,
                new ConsentUpdateRequest(
                        "OPTIONAL_INFO", "v1.0", false
                )
        );

        verify(documentRecordRepository, never())
                .deleteRecords(any());
    }


    @Test
    void shouldDeleteDocumentRecordsWhenConsentAlreadyWithdrawn() {
        Long userId = 1L;

        User user = mock(User.class);

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        UserConsent previousConsent = mock(UserConsent.class);

        when(previousConsent.getAgreed()).thenReturn(false);
        when(previousConsent.getDocVersion()).thenReturn("v1.0");

        when(userConsentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        userId, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(previousConsent));

        ConsentUpdateRequest request =
                new ConsentUpdateRequest(
                        "DOC_RECORD",
                        "v1.0",
                        false
                );

        authService.updateConsent(userId, request);

        verify(documentRecordRepository)
                .deleteRecords(userId);
    }


    @Test
    void shouldThrowExceptionWhenDocumentRecordDeletionFails() {
        Long userId = 1L;

        User user = mock(User.class);

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        when(userConsentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        userId, "DOC_RECORD"
                ))
                .thenReturn(Optional.empty());

        doThrow(new org.springframework.dao.DataAccessResourceFailureException(
                "서류 기록 삭제 실패"
        )).when(documentRecordRepository).deleteRecords(userId);

        ConsentUpdateRequest request =
                new ConsentUpdateRequest(
                        "DOC_RECORD",
                        "v1.0",
                        false
                );

        assertThrows(
                org.springframework.dao.DataAccessResourceFailureException.class,
                () -> authService.updateConsent(userId, request)
        );

        verify(documentRecordRepository).deleteRecords(userId);
        verify(userConsentRepository, never()).save(any(UserConsent.class));
    }
}