package com.bokdeongi.backend.home.service;

import com.bokdeongi.backend.home.dto.response.HomeCalendarResponse;
import com.bokdeongi.backend.home.repository.HomeCalendarRepository;
import com.bokdeongi.backend.home.repository.HomeCalendarRepository.CalendarBenefitRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HomeCalendarServiceTest {

    private HomeCalendarRepository repository;
    private HomeCalendarService service;

    @BeforeEach
    void setUp() {
        repository = mock(HomeCalendarRepository.class);

        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-11-19T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        service = new HomeCalendarService(repository, fixedClock);
    }

    /**
     * HOME-03 상시 신청 혜택 집계 테스트.
     *
     * 상시 신청 혜택은 날짜별 일정에 표시하지 않고
     * alwaysCount에 집계해야 한다.
     */
    @Test
    void alwaysBenefitShouldIncreaseAlwaysCount() {
        // given
        long userId = 137L;

        CalendarBenefitRow benefit = new CalendarBenefitRow(
                1L,
                "상시 신청 테스트 혜택",
                null,
                null,
                null,
                "ALWAYS",
                null
        );

        when(repository.findUserBenefits(userId))
                .thenReturn(List.of(benefit));

        // when
        HomeCalendarResponse response =
                service.getCalendar(userId, 2026, 11);

        // then
        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.month()).isEqualTo(11);
        assertThat(response.events()).isEmpty();
        assertThat(response.alwaysCount()).isEqualTo(1);
        assertThat(response.fallbackUsed()).isFalse();

        verify(repository).findUserBenefits(userId);
        verify(repository, never()).findUpcomingDeadlines(
                any(LocalDate.class),
                any(LocalDate.class)
        );
    }

    /**
     * HOME-03 기관별 상이 및 신청 불필요 혜택 제외 테스트.
     *
     * VARIES와 NO_APPLICATION은 캘린더 일정에 표시하지 않고
     * 상시 신청 건수에도 포함하지 않는다.
     */
    @Test
    void variesAndNoApplicationShouldNotCreateEvents() {
        // given
        long userId = 137L;

        CalendarBenefitRow varies = new CalendarBenefitRow(
                2L,
                "기관별 신청기간 상이 혜택",
                null,
                null,
                null,
                "VARIES",
                null
        );

        CalendarBenefitRow noApplication = new CalendarBenefitRow(
                3L,
                "신청 불필요 혜택",
                null,
                null,
                null,
                "NO_APPLICATION",
                null
        );

        when(repository.findUserBenefits(userId))
                .thenReturn(List.of(varies, noApplication));

        // when
        HomeCalendarResponse response =
                service.getCalendar(userId, 2026, 11);

        // then
        assertThat(response.events()).isEmpty();
        assertThat(response.alwaysCount()).isZero();
        assertThat(response.fallbackUsed()).isFalse();

        verify(repository).findUserBenefits(userId);
        verify(repository, never()).findUpcomingDeadlines(
                any(LocalDate.class),
                any(LocalDate.class)
        );
    }

    /**
     * HOME-03 접수 시작일, 신청 마감일, 결과 발표일 테스트.
     *
     * 요청한 월에 포함된 날짜만 일정으로 반환하며,
     * 날짜순으로 정렬되는지 검증한다.
     */
    @Test
    void shouldReturnThreeEventTypesInDateOrder() {
        // given
        long userId = 137L;

        CalendarBenefitRow benefit = new CalendarBenefitRow(
                10L,
                "청년 지원 테스트 혜택",
                LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 12, 10),
                "PARSED",
                "DAY"
        );

        when(repository.findUserBenefits(userId))
                .thenReturn(List.of(benefit));

        // when
        HomeCalendarResponse november =
                service.getCalendar(userId, 2026, 11);

        // then
        assertThat(november.events()).hasSize(2);

        assertThat(november.events().get(0).eventType())
                .isEqualTo("APPLY_START");
        assertThat(november.events().get(0).date())
                .isEqualTo(LocalDate.of(2026, 11, 5));

        assertThat(november.events().get(1).eventType())
                .isEqualTo("APPLY_END");
        assertThat(november.events().get(1).date())
                .isEqualTo(LocalDate.of(2026, 11, 20));

        // 12월에는 결과 발표 일정만 표시
        HomeCalendarResponse december =
                service.getCalendar(userId, 2026, 12);

        assertThat(december.events()).hasSize(1);
        assertThat(december.events().get(0).eventType())
                .isEqualTo("RESULT");
        assertThat(december.events().get(0).date())
                .isEqualTo(LocalDate.of(2026, 12, 10));

        assertThat(november.fallbackUsed()).isFalse();
        assertThat(december.fallbackUsed()).isFalse();
    }

    /**
     * HOME-03 관심·진행 중 혜택이 없을 때 대체 일정 조회 테스트.
     *
     * 사용자 혜택이 비어 있으면 마감 임박 혜택을 조회하고
     * fallbackUsed를 true로 반환해야 한다.
     */
    @Test
    void shouldUseUpcomingDeadlinesWhenUserHasNoBenefits() {
        // given
        long userId = 137L;

        LocalDate startDate = LocalDate.of(2026, 11, 19);
        LocalDate endDate = LocalDate.of(2026, 11, 30);

        CalendarBenefitRow fallbackBenefit = new CalendarBenefitRow(
                20L,
                "마감 임박 테스트 혜택",
                null,
                LocalDate.of(2026, 11, 25),
                null,
                "PARSED",
                "DAY"
        );

        when(repository.findUserBenefits(userId))
                .thenReturn(List.of());

        when(repository.findUpcomingDeadlines(startDate, endDate))
                .thenReturn(List.of(fallbackBenefit));

        // when
        HomeCalendarResponse response =
                service.getCalendar(userId, 2026, 11);

        // then
        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).benefitId()).isEqualTo(20L);
        assertThat(response.events().get(0).eventType())
                .isEqualTo("APPLY_END");
        assertThat(response.events().get(0).date())
                .isEqualTo(LocalDate.of(2026, 11, 25));

        verify(repository).findUserBenefits(userId);
        verify(repository).findUpcomingDeadlines(startDate, endDate);
    }

    @Test
    void monthPrecisionShouldNotCreateEvents() {
        long userId = 137L;

        CalendarBenefitRow benefit = new CalendarBenefitRow(
                30L,
                "월 단위 일정 테스트 혜택",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                null,
                "PARSED",
                "MONTH"
        );

        when(repository.findUserBenefits(userId))
                .thenReturn(List.of(benefit));

        HomeCalendarResponse response =
                service.getCalendar(userId, 2026, 11);

        assertThat(response.events()).isEmpty();
        assertThat(response.alwaysCount()).isZero();
        assertThat(response.fallbackUsed()).isFalse();
    }

    @Test
    void invalidMonthShouldThrowException() {
        assertThatThrownBy(() ->
                service.getCalendar(137L, 2026, 13)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("월은 1부터 12까지 입력해야 합니다.");
    }

    @Test
    void invalidYearShouldThrowException() {
        assertThatThrownBy(() ->
                service.getCalendar(137L, 0, 11)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("올바른 연도를 입력해주세요.");
    }
}