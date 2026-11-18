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
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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
    }

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

        // 기존 민감정보가 저장되어 있는 상황을 설정한다.
        // 엔티티에서는 JSON 문자열을 관리하고,
        // 실제 DB 저장 시 JPA Converter가 암호화한다.
        user.updateProfileStep4("[\"JA0301\"]");

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // 기존 민감정보 수집 동의 상태
        UserConsent previousConsent =
                new UserConsent(user, "SENSITIVE", "v1", true);

        when(userConsentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        eq(userId), eq("SENSITIVE")
                ))
                .thenReturn(Optional.of(previousConsent));

        ConsentUpdateRequest request =
                new ConsentUpdateRequest("SENSITIVE", "v1", false);

        // When: 동의 철회
        authService.updateConsent(userId, request);

        // Then: 기존 민감정보 삭제
        assertNull(user.getSensitiveEnc());

        // 철회 이력 저장
        verify(userConsentRepository).save(
                argThat(consent ->
                        "SENSITIVE".equals(consent.getConsentType())
                                && Boolean.FALSE.equals(consent.getAgreed())
                )
        );

        // 재매칭 이벤트 발행
        verify(eventPublisher).publishEvent(
                any(ProfileChangedEvent.class)
        );
    }

    @Test
    void shouldNotRepeatSensitiveConsentWithdrawal() {
        // Given: 이미 동의를 철회하고 민감정보도 삭제한 사용자
        Long userId = 1L;

        User user = new User(
                "test@example.com",
                "passwordHash",
                "테스트",
                "01012345678",
                null
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        UserConsent previousConsent =
                new UserConsent(user, "SENSITIVE", "v1", false);

        when(userConsentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        eq(userId), eq("SENSITIVE")
                ))
                .thenReturn(Optional.of(previousConsent));

        ConsentUpdateRequest request =
                new ConsentUpdateRequest("SENSITIVE", "v1", false);

        int originalVersion = user.getProfileVersion();

        // When: 동일한 동의 철회 요청
        authService.updateConsent(userId, request);

        // Then: 민감정보와 프로필 버전 유지
        assertNull(user.getSensitiveEnc());
        assertEquals(originalVersion, user.getProfileVersion());

        // 중복 동의 이력 저장 방지
        verify(userConsentRepository, never())
                .save(any(UserConsent.class));

        // 불필요한 재매칭 이벤트 방지
        verifyNoInteractions(eventPublisher);
    }
}