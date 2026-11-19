package com.bokdeongi.backend.home.service;

import com.bokdeongi.backend.home.dto.response.HomeActivityResponse;
import com.bokdeongi.backend.home.repository.HomeActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomeActivityServiceTest {

    private HomeActivityRepository repository;
    private HomeActivityService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(HomeActivityRepository.class);
        service = new HomeActivityService(repository);
    }

    /**
     * 적격 혜택이 없으면 주간 요약 카드를 숨긴다.
     */
    @Test
    void shouldHideWeeklySummaryWhenNoEligibleBenefits() {
        // given
        long userId = 137L;

        when(repository.findEligibleActivities(userId))
                .thenReturn(List.of());

        when(repository.findLastLoginAt(userId))
                .thenReturn(null);

        // when
        HomeActivityResponse response = service.getActivity(userId);

        // then
        assertTrue(response.newEligibleBenefits().isEmpty());

        HomeActivityResponse.WeeklySummary summary =
                response.weeklySummary();

        assertFalse(summary.visible());
        assertEquals(0, summary.newEligibleCount());
        assertEquals(0, summary.closingSoonCount());
        assertEquals(0, summary.changedBenefitCount());

        verify(repository).findEligibleActivities(userId);
    }

    /**
     * 적격 혜택이 존재하지만 주간 요약 대상이 없으면
     * 주간 요약 카드를 숨긴다.
     */
    @Test
    void shouldHideWeeklySummaryWhenNoWeeklyActivity() {
        // given
        long userId = 137L;

        HomeActivityRepository.EligibleActivityRow row =
                new HomeActivityRepository.EligibleActivityRow(
                        3L,
                        "테스트 혜택",
                        "ELIGIBLE",
                        LocalDateTime.of(2026, 1, 1, 10, 0),
                        null,
                        LocalDateTime.of(2026, 1, 1, 10, 0),
                        null,
                        null
                );

        when(repository.findEligibleActivities(userId))
                .thenReturn(List.of(row));

        when(repository.findLastLoginAt(userId))
                .thenReturn(LocalDateTime.of(2026, 1, 2, 10, 0));

        // when
        HomeActivityResponse response = service.getActivity(userId);

        // then
        assertEquals(1, response.newEligibleBenefits().size());

        HomeActivityResponse.WeeklySummary summary =
                response.weeklySummary();

        assertFalse(summary.visible());
        assertEquals(0, summary.newEligibleCount());
        assertEquals(0, summary.closingSoonCount());
        assertEquals(0, summary.changedBenefitCount());
    }


    /**
     * 이번 주 변경된 적격 혜택이 있으면
     * 변경 공고 수를 집계하고 주간 요약 카드를 표시한다.
     */
    @Test
    void shouldShowWeeklySummaryWhenBenefitChangedThisWeek() {
        // given
        long userId = 137L;

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        LocalDate weekStart = today.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );

        LocalDateTime changedAt = weekStart.atTime(10, 0);

        HomeActivityRepository.EligibleActivityRow row =
                new HomeActivityRepository.EligibleActivityRow(
                        3L,
                        "변경 공고 테스트",
                        "ELIGIBLE",
                        LocalDateTime.of(2026, 1, 1, 10, 0),
                        null,
                        LocalDateTime.of(2026, 1, 1, 10, 0),
                        null,
                        changedAt
                );

        when(repository.findEligibleActivities(userId))
                .thenReturn(List.of(row));

        when(repository.findLastLoginAt(userId))
                .thenReturn(LocalDateTime.of(2026, 1, 2, 10, 0));

        // when
        HomeActivityResponse response = service.getActivity(userId);

        // then
        HomeActivityResponse.WeeklySummary summary =
                response.weeklySummary();

        assertTrue(summary.visible());
        assertEquals(0, summary.newEligibleCount());
        assertEquals(0, summary.closingSoonCount());
        assertEquals(1, summary.changedBenefitCount());

        assertEquals(1, response.newEligibleBenefits().size());
    }
}