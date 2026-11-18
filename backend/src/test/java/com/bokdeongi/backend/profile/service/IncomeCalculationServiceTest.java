package com.bokdeongi.backend.profile.service;

import com.bokdeongi.backend.profile.entity.MedianIncomeStandard;
import com.bokdeongi.backend.profile.repository.MedianIncomeStandardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncomeCalculationServiceTest {

    @Mock
    private MedianIncomeStandardRepository repository;

    private IncomeCalculationService incomeCalculationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        incomeCalculationService = new IncomeCalculationService(repository);
    }

    @Test
    void monthlyIncomeShouldCalculateMedianIncomePercentage() {
        // Given: 2026년 4인 가구 기준 중위소득 6,494,738원
        short year = 2026;
        short householdSize = 4;

        MedianIncomeStandard standard =
                org.mockito.Mockito.mock(MedianIncomeStandard.class);

        when(standard.getMonthlyAmount()).thenReturn(6_494_738);
        when(repository.findByYearAndHouseholdSize(year, householdSize))
                .thenReturn(Optional.of(standard));

        // When: 월소득 300만 원을 2026년 기준으로 환산
        BigDecimal result = incomeCalculationService.recalculateMedianIncomePct(
                householdSize,
                "MONTHLY",
                new BigDecimal("3000000"),
                year
        );

        // Then: 기준 중위소득 대비 46.2%
        assertEquals(new BigDecimal("46.2"), result);
    }

    @Test
    void eightPersonHouseholdShouldApplyAdditionalAmount() {
        // Given: 2026년 7인 가구 기준액과 8인 이상 가산 규칙
        short year = 2026;
        short householdSize = 7;

        MedianIncomeStandard standard =
                org.mockito.Mockito.mock(MedianIncomeStandard.class);

        when(standard.getMonthlyAmount()).thenReturn(9_515_150);
        when(repository.findByYearAndHouseholdSize(year, householdSize))
                .thenReturn(Optional.of(standard));

        // When: 8인 가구, 월소득 500만 원
        BigDecimal result = incomeCalculationService.recalculateMedianIncomePct(
                (short) 8,
                "MONTHLY",
                new BigDecimal("5000000"),
                year
        );

        // Then: 8인 가구 기준액 10,474,348원 → 47.7%
        assertEquals(new BigDecimal("47.7"), result);
    }

    @Test
    void shouldRejectYearWithoutIncomeStandard() {
        // Given: 2027년 4인 가구 기준표가 없는 상황
        short year = 2027;
        short householdSize = 4;

        when(repository.findByYearAndHouseholdSize(year, householdSize))
                .thenReturn(Optional.empty());

        // When & Then: 기준표가 없으면 400 오류 발생
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> incomeCalculationService.recalculateMedianIncomePct(
                        householdSize,
                        "MONTHLY",
                        new BigDecimal("3000000"),
                        year
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void shouldRejectUnsupportedIncomeTypeForRecalculation() {
        // Given: 금액 기반 재계산을 지원하지 않는 입력 방식
        short year = 2026;

        // When & Then: UNKNOWN은 재계산 대상이 아니므로 400 오류
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> incomeCalculationService.recalculateMedianIncomePct(
                        (short) 4,
                        "UNKNOWN",
                        new BigDecimal("3000000"),
                        year
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void shouldCheckIncomeStandardReadiness() {
        // Given: 2026년 1~7인 가구 기준표가 모두 등록된 상황
        short year = 2026;

        for (short size = 1; size <= 7; size++) {
            when(repository.findByYearAndHouseholdSize(year, size))
                    .thenReturn(Optional.of(
                            org.mockito.Mockito.mock(MedianIncomeStandard.class)
                    ));
        }

        // Then: 2026년 기준표는 준비 완료
        assertEquals(true, incomeCalculationService.isIncomeStandardReady(year));

        // Given: 2027년 기준표는 등록되지 않은 상황
        short nextYear = 2027;

        when(repository.findByYearAndHouseholdSize(nextYear, (short) 1))
                .thenReturn(Optional.empty());

        // Then: 2027년 기준표는 준비되지 않음
        assertEquals(false, incomeCalculationService.isIncomeStandardReady(nextYear));
    }
}