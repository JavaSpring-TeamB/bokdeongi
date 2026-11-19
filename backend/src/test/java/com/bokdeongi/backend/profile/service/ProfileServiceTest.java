package com.bokdeongi.backend.profile.service;

import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.entity.UserConsent;
import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import com.bokdeongi.backend.auth.repository.UserRepository;
import com.bokdeongi.backend.eligibility.service.ProfileChangedEvent;
import com.bokdeongi.backend.profile.dto.request.ProfileStep4Request;
import com.bokdeongi.backend.profile.repository.RegionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import com.bokdeongi.backend.profile.dto.request.ProfileStep1Request;
import com.bokdeongi.backend.profile.entity.Region;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProfileServiceTest {

    private UserRepository userRepository;
    private IncomeCalculationService incomeCalculationService;
    private ApplicationEventPublisher eventPublisher;
    private ProfileService profileService;
    private RegionRepository regionRepository;
    private UserConsentRepository userConsentRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        incomeCalculationService = mock(IncomeCalculationService.class);
        objectMapper = mock(ObjectMapper.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        userConsentRepository = mock(UserConsentRepository.class);
        regionRepository = mock(RegionRepository.class);

        // 테스트에서는 기준 중위소득 자료가 준비된 것으로 설정
        when(incomeCalculationService.isIncomeStandardReady(anyShort()))
                .thenReturn(true);

        // 민감정보 암호화는 JPA Converter에서 처리한다.
        // 따라서 ProfileService에 암호화 서비스를 직접 주입하지 않는다.
        profileService = new ProfileService(
                userRepository,
                regionRepository,
                objectMapper,
                userConsentRepository,
                eventPublisher,
                "v1.0",
                incomeCalculationService
        );
    }

    /**
     * PROF-03: 새로운 연도의 기준 중위소득으로 재계산한다.
     */
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

    /**
     * PROF-03: 동일한 연도에는 소득을 재계산하지 않는다.
     */
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

    /**
     * PROF-03: 소득을 모르는 사용자는 자동 재계산 대상이 아니다.
     */
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

    /**
     * PROF-03: 소득 비율을 직접 입력한 사용자는 재계산하지 않는다.
     */
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

    /**
     * PROF-03: 기준 중위소득 자료가 없으면 재계산을 거부한다.
     */
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

    /**
     * PROF-03: 프로필 조회 시 연도가 변경되었다면 소득을 재계산한다.
     */
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

    /**
     * PROF-04: 민감정보 동의 후 입력을 건너뛸 수 있다.
     */
    @Test
    void shouldSkipSensitiveInfoWhenAgreed() {
        // Given: 프로필 1~3단계를 완료한 사용자
        Long userId = 1L;

        User user = new User(
                "test@example.com",
                "passwordHash",
                "테스트",
                "01012345678",
                null
        );

        user.updateProfileStep3(
                "[]",
                "SINGLE",
                (short) 0,
                null,
                "UNKNOWN"
        );

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        ProfileStep4Request request = new ProfileStep4Request(
                true,
                "v1.0",
                null,  // consentId: 최초 동의 이력이 없는 경우
                null   // sensitiveCodes
        );

        // When: 민감정보 입력을 건너뛰고 저장
        profileService.saveProfileStep4(userId, request);

        // Then: 민감정보는 NULL 상태를 유지한다.
        assertNull(user.getSensitiveEnc());

        // 동의 이력은 저장되어야 한다.
        verify(userConsentRepository).save(
                argThat(consent ->
                        "SENSITIVE".equals(consent.getConsentType())
                                && Boolean.TRUE.equals(consent.getAgreed())
                )
        );

        // 건너뛰기 시 JSON 변환을 수행하지 않는다.
        verify(objectMapper, never()).writeValueAsString(any());

        // 프로필 변경 이벤트가 발생해야 한다.
        verify(eventPublisher).publishEvent(
                any(ProfileChangedEvent.class)
        );
    }

    /**
     * PROF-04: 민감정보 '해당 없음'을 선택하면 빈 목록을 저장한다.
     */
    @Test
    void shouldSaveEmptySensitiveCodesWhenAgreed() {
        // Given: 프로필 3단계까지 완료한 사용자
        Long userId = 1L;

        User user = new User(
                "test@example.com",
                "passwordHash",
                "테스트",
                "01012345678",
                null
        );

        user.updateProfileStep3(
                "[]",
                "SINGLE",
                (short) 0,
                null,
                "UNKNOWN"
        );

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // 빈 목록을 JSON 문자열로 변환하도록 설정한다.
        when(objectMapper.writeValueAsString(List.of()))
                .thenReturn("[]");

        ProfileStep4Request request = new ProfileStep4Request(
                true,
                "v1.0",
                null,
                List.of()
        );

        // When: '해당 없음'을 선택하고 저장
        profileService.saveProfileStep4(userId, request);

        // Then: 빈 목록 JSON 문자열이 엔티티에 저장되어야 한다.
        assertEquals("[]", user.getSensitiveEnc());

        // JSON 변환이 수행되어야 한다.
        verify(objectMapper).writeValueAsString(List.of());

        // 동의 이력과 프로필 변경 이벤트 확인
        verify(userConsentRepository).save(any(UserConsent.class));

        verify(eventPublisher).publishEvent(
                any(ProfileChangedEvent.class)
        );
    }

    /**
     * PROF-04: 동의 후 선택한 민감정보 코드를 저장한다.
     */
    @Test
    void shouldSaveSelectedSensitiveCodesWhenAgreed() {
        // Given: 프로필 3단계까지 완료한 사용자
        Long userId = 1L;

        User user = new User(
                "test@example.com",
                "passwordHash",
                "테스트",
                "01012345678",
                null
        );

        user.updateProfileStep3(
                "[]",
                "SINGLE",
                (short) 0,
                null,
                "UNKNOWN"
        );

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // 선택한 민감정보 코드를 JSON 문자열로 변환한다.
        when(objectMapper.writeValueAsString(List.of("JA0301")))
                .thenReturn("[\"JA0301\"]");

        ProfileStep4Request request = new ProfileStep4Request(
                true,
                "v1.0",
                null,
                List.of("JA0301")
        );

        // When: 민감정보 코드 저장
        profileService.saveProfileStep4(userId, request);

        // Then: JSON 문자열이 엔티티에 저장되어야 한다.
        assertEquals("[\"JA0301\"]", user.getSensitiveEnc());

        // JSON 변환이 수행되어야 한다.
        verify(objectMapper).writeValueAsString(List.of("JA0301"));

        // 동의 이력과 프로필 변경 이벤트 확인
        verify(userConsentRepository).save(any(UserConsent.class));

        verify(eventPublisher).publishEvent(
                any(ProfileChangedEvent.class)
        );
    }

    /**
     * PROF-04: 민감정보 동의 철회 시 기존 정보를 삭제한다.
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

        user.updateProfileStep3(
                "[]",
                "SINGLE",
                (short) 0,
                null,
                "UNKNOWN"
        );

        // 기존 민감정보는 엔티티에서 JSON 문자열로 관리한다.
        user.updateProfileStep4("[\"JA0301\"]");

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        ProfileStep4Request request = new ProfileStep4Request(
                false,
                "v1.0",
                null,
                null
        );

        // When: 민감정보 동의 철회
        profileService.saveProfileStep4(userId, request);

        // Then: 기존 민감정보가 삭제되어야 한다.
        assertNull(user.getSensitiveEnc());

        // 미동의 이력 저장
        verify(userConsentRepository).save(
                argThat(consent ->
                        "SENSITIVE".equals(consent.getConsentType())
                                && Boolean.FALSE.equals(consent.getAgreed())
                )
        );

        // 미동의 시 JSON 변환을 수행하지 않는다.
        verify(objectMapper, never()).writeValueAsString(any());

        // 재매칭을 위한 프로필 변경 이벤트 발행
        verify(eventPublisher).publishEvent(
                any(ProfileChangedEvent.class)
        );
    }

    /**
     * PROF-05: 기존 프로필 수정 시 버전이 증가하고
     * 진행 단계는 유지되며 재매칭 이벤트가 발행된다.
     */
    @Test
    void shouldUpdateExistingProfileAndPublishEvent() {
        // Given: 기존 프로필이 4단계까지 작성된 사용자
        Long userId = 137L;

        User user = new User(
                "prof05test@example.com",
                "passwordHash",
                "테스트",
                "01012345678",
                null
        );

        user.updateProfileStep1(
                java.time.LocalDate.of(2000, 1, 1),
                "M",
                "11110",
                "서울특별시",
                "종로구"
        );

        user.updateProfileStep2(
                (short) 1,
                "UNKNOWN",
                null,
                null,
                null,
                "JEONSE",
                true,
                "[]"
        );

        user.updateProfileStep3(
                "[]",
                "SINGLE",
                (short) 0,
                null,
                "UNKNOWN"
        );

        user.updateProfileStep4(null);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        int originalVersion = user.getProfileVersion();
        short originalStep = user.getProfileStep();

        // When: 기존 프로필의 거주 지역 수정
        user.updateProfileStep1(
                java.time.LocalDate.of(2000, 1, 1),
                "M",
                "11140",
                "서울특별시",
                "중구"
        );

        // Then: 변경 정보 반영
        assertEquals("11140", user.getRegionCode());
        assertEquals("중구", user.getSigunguName());

        // 프로필 버전 증가
        assertEquals(originalVersion + 1, user.getProfileVersion());

        // 기존 프로필 진행 단계 유지
        assertEquals(originalStep, user.getProfileStep());
    }

    /**
     * PROF-05: 프로필 수정 시 버전 증가 및 재매칭 이벤트 발행
     */
    @Test
    void shouldPublishEventWhenUpdatingProfile() {
        // Given
        Long userId = 137L;

        User user = new User(
                "prof05test@example.com",
                "passwordHash",
                "테스트",
                "01012345678",
                null
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        ProfileStep1Request request = new ProfileStep1Request(
                LocalDate.of(2000, 1, 1),
                "M",
                "11140",
                "서울특별시",
                "중구"
        );

        Region region = mock(Region.class);

        when(region.getRegionCode()).thenReturn("11140");
        when(region.getSidoName()).thenReturn("서울특별시");
        when(region.getSigunguName()).thenReturn("중구");

        when(regionRepository.findById("11140"))
                .thenReturn(Optional.of(region));

        int originalVersion = user.getProfileVersion();

        // When
        profileService.saveProfileStep1(userId, request);

        // Then
        assertEquals(originalVersion + 1, user.getProfileVersion());
        assertEquals("11140", user.getRegionCode());

        verify(eventPublisher, times(1))
                .publishEvent(new ProfileChangedEvent(userId));
    }
}