package com.bokdeongi.backend.auth.service;

import com.bokdeongi.backend.auth.dto.request.ConsentUpdateRequest;
import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.entity.UserConsent;
import com.bokdeongi.backend.auth.jwt.JwtTokenProvider;
import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import com.bokdeongi.backend.auth.repository.UserRepository;
import com.bokdeongi.backend.eligibility.service.ProfileChangedEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

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

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userConsentRepository = mock(UserConsentRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        authService = new AuthService(
                userRepository,
                userConsentRepository,
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
}