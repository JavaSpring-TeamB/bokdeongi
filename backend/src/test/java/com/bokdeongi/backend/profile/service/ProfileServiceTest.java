
package com.bokdeongi.backend.profile.service;

import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import com.bokdeongi.backend.auth.repository.UserRepository;
import com.bokdeongi.backend.common.crypto.FieldEncryptionService;
import com.bokdeongi.backend.eligibility.service.ProfileChangedEvent;
import com.bokdeongi.backend.profile.repository.RegionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assertions.assertNull;

class ProfileServiceTest {

    private UserRepository userRepository;
    private IncomeCalculationService incomeCalculationService;
    private ApplicationEventPublisher eventPublisher;
    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        incomeCalculationService = mock(IncomeCalculationService.class);

        // 테스트에서는 기준 중위소득 자료가 준비된 것으로 설정
        when(incomeCalculationService.isIncomeStandardReady(anyShort()))
                .thenReturn(true);
        eventPublisher = mock(ApplicationEventPublisher.class);

        profileService = new ProfileService(
                userRepository,
                mock(RegionRepository.class),
                mock(ObjectMapper.class),
                mock(FieldEncryptionService.class),
                mock(UserConsentRepository.class),
                eventPublisher,
                "v1",
                incomeCalculationService
        );
    }

    @Test
    void shouldRecalculateIncomeForNewYear() {
        // Given: 2026년 기준으로 소득 정보가 저장된 사용자
        Long userId = 1L;
        User user = new User(
                "test@example.com",
                "passwordHash",
                "테스트",
                "01012345678",
                null
        );

        user.updateProfileStep2(
                (short) 4,
                "MONTHLY",
                new BigDecimal("3000000"),
                (short) 2026,
                new BigDecimal("46.2"),
                "JEONSE",
                true,
                "[]"
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(incomeCalculationService.recalculateMedianIncomePct(
                eq((short) 4),
                eq("MONTHLY"),
                eq(new BigDecimal("3000000")),
                eq((short) 2027)
        )).thenReturn(new BigDecimal("44.0"));

        // When: 2027년 기준으로 재계산
        profileService.recalculateIncomeForYear(userId, (short) 2027);

        // Then: 소득 기준 연도와 비율이 변경되어야 한다.
        assertEquals((short) 2027, user.getIncomeBaseYear());
        assertEquals(new BigDecimal("44.0"), user.getMedianIncomePct());

        // 기존 소득 금액은 변경되지 않아야 한다.
        assertEquals(new BigDecimal("3000000"), user.getIncomeAmount());

        // 프로필 변경 이벤트가 발행되어야 한다.
        verify(eventPublisher).publishEvent(
                any(ProfileChangedEvent.class)
        );
    }

    @Test
    void shouldSkipRecalculationForSameYear() {
        // Given: 이미 2026년 기준으로 계산된 사용자
        Long userId = 1L;

        User user = new User(
                "test@example.com",
                "passwordHash",
                "테스트",
                "01012345678",
                null
        );

        user.updateProfileStep2(
                (short) 4,
                "MONTHLY",
                new BigDecimal("3000000"),
                (short) 2026,
                new BigDecimal("46.2"),
                "JEONSE",
                true,
                "[]"
        );

        int originalVersion = user.getProfileVersion();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // When: 동일한 2026년 기준으로 재계산 요청
        profileService.recalculateIncomeForYear(userId, (short) 2026);

        // Then: 기존 소득 정보와 프로필 버전 유지
        assertEquals((short) 2026, user.getIncomeBaseYear());
        assertEquals(new BigDecimal("46.2"), user.getMedianIncomePct());
        assertEquals(originalVersion, user.getProfileVersion());

        // 재계산 서비스와 이벤트 발행이 호출되지 않아야 한다.
        verifyNoInteractions(incomeCalculationService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldSkipRecalculationForUnknownIncome() {
        // Given: 소득을 모름으로 선택한 사용자
        Long userId = 1L;

        User user = new User(
                "test@example.com",
                "passwordHash",
                "테스트",
                "01012345678",
                null
        );

        user.updateProfileStep2(
                (short) 4,
                "UNKNOWN",
                null,
                null,
                null,
                "JEONSE",
                true,
                "[]"
        );

        int originalVersion = user.getProfileVersion();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // When: 2027년 기준으로 재계산 요청
        profileService.recalculateIncomeForYear(userId, (short) 2027);

        // Then: 소득 정보와 프로필 버전이 변경되지 않아야 한다.
        assertNull(user.getIncomeBaseYear());
        assertNull(user.getMedianIncomePct());
        assertEquals(originalVersion, user.getProfileVersion());

        // 계산 및 이벤트 발행이 발생하지 않아야 한다.
        verifyNoInteractions(incomeCalculationService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldSkipRecalculationForBracketIncome() {
        // Given: 기준 중위소득 비율을 직접 입력한 사용자
        Long userId = 1L;

        User user = new User(
                "test@example.com",
                "passwordHash",
                "테스트",
                "01012345678",
                null
        );

        user.updateProfileStep2(
                (short) 4,
                "BRACKET",
                null,
                (short) 2026,
                new BigDecimal("50.0"),
                "JEONSE",
                true,
                "[]"
        );

        int originalVersion = user.getProfileVersion();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // When: 2027년 기준으로 재계산 요청
        profileService.recalculateIncomeForYear(userId, (short) 2027);

        // Then: 기존 비율과 기준 연도 유지
        assertEquals((short) 2026, user.getIncomeBaseYear());
        assertEquals(new BigDecimal("50.0"), user.getMedianIncomePct());
        assertEquals(originalVersion, user.getProfileVersion());

        // 자동 재계산 및 이벤트 발행이 없어야 한다.
        verifyNoInteractions(incomeCalculationService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldRejectRecalculationWhenStandardIsNotReady() {
        // Given: 2026년 소득 정보가 저장된 사용자
        Long userId = 1L;

        User user = new User(
                "test@example.com",
                "passwordHash",
                "테스트",
                "01012345678",
                null
        );

        user.updateProfileStep2(
                (short) 4,
                "MONTHLY",
                new BigDecimal("3000000"),
                (short) 2026,
                new BigDecimal("46.2"),
                "JEONSE",
                true,
                "[]"
        );

        int originalVersion = user.getProfileVersion();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // 2027년 기준표가 준비되지 않은 상황
        when(incomeCalculationService.isIncomeStandardReady((short) 2027))
                .thenReturn(false);

        // When & Then: 재계산 요청 시 400 오류 발생
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> profileService.recalculateIncomeForYear(
                        userId, (short) 2027
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        // 기존 소득 정보가 유지되어야 한다.
        assertEquals((short) 2026, user.getIncomeBaseYear());
        assertEquals(new BigDecimal("46.2"), user.getMedianIncomePct());
        assertEquals(originalVersion, user.getProfileVersion());

        // 실제 계산 및 이벤트 발행은 발생하지 않아야 한다.
        verify(incomeCalculationService, never())
                .recalculateMedianIncomePct(
                        anyShort(), anyString(), any(), anyShort()
                );

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldRecalculateIncomeWhenGettingProfile() {
        // Given: 이전 연도 기준으로 소득이 계산된 사용자
        Long userId = 1L;
        short currentYear = (short) Year.now().getValue();
        short previousYear = (short) (currentYear - 1);

        User user = new User(
                "test@example.com",
                "passwordHash",
                "테스트",
                "01012345678",
                null
        );

        user.updateProfileStep2(
                (short) 4,
                "MONTHLY",
                new BigDecimal("3000000"),
                previousYear,
                new BigDecimal("50.0"),
                "JEONSE",
                true,
                "[]"
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(incomeCalculationService.recalculateMedianIncomePct(
                eq((short) 4),
                eq("MONTHLY"),
                eq(new BigDecimal("3000000")),
                eq(currentYear)
        )).thenReturn(new BigDecimal("46.2"));

        // When: 프로필 조회
        profileService.getProfile(userId);

        // Then: 현재 연도 기준으로 재계산되어야 한다.
        assertEquals(currentYear, user.getIncomeBaseYear());
        assertEquals(new BigDecimal("46.2"), user.getMedianIncomePct());

        // 프로필 변경 이벤트 발행 확인
        verify(eventPublisher).publishEvent(
                any(ProfileChangedEvent.class)
        );
    }
}