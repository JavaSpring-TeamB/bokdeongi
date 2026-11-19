package com.bokdeongi.backend.home.service;

import com.bokdeongi.backend.home.dto.response.HomeCalendarResponse;
import com.bokdeongi.backend.home.dto.response.HomeCalendarResponse.CalendarEvent;
import com.bokdeongi.backend.home.repository.HomeCalendarRepository;
import com.bokdeongi.backend.home.repository.HomeCalendarRepository.CalendarBenefitRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * HOME-03 홈 신청 일정 캘린더 서비스.
 *
 * 관심·진행 중 혜택의 월별 일정을 조회하며,
 * 대상 혜택이 없는 경우 마감 임박 혜택을 대신 제공한다.
 */
@Service
public class HomeCalendarService {

    private final HomeCalendarRepository repository;
    private final Clock clock;

    public HomeCalendarService(
            HomeCalendarRepository repository,
            Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 요청한 연도와 월에 해당하는 신청 일정 조회.
     *
     * @param userId 로그인 사용자 ID
     * @param year 조회 연도
     * @param month 조회 월
     * @return 월별 캘린더 데이터
     */
    @Transactional(readOnly = true)
    public HomeCalendarResponse getCalendar(
            long userId,
            int year,
            int month
    ) {
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("올바른 연도를 입력해주세요.");
        }

        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("월은 1부터 12까지 입력해야 합니다.");
        }

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<CalendarBenefitRow> benefits =
                repository.findUserBenefits(userId);

        boolean fallbackUsed = benefits.isEmpty();

        if (fallbackUsed) {
            LocalDate today = LocalDate.now(clock);

            // 현재 월은 오늘 이후 마감 일정만 조회
            LocalDate fallbackStartDate = startDate;

            if (yearMonth.equals(YearMonth.from(today))) {
                fallbackStartDate = today;
            }

            benefits = repository.findUpcomingDeadlines(
                    fallbackStartDate,
                    endDate
            );
        }

        List<CalendarEvent> events = new ArrayList<>();
        int alwaysCount = 0;

        for (CalendarBenefitRow benefit : benefits) {
            String dateStatus = benefit.dateStatus();

            if ("NO_APPLICATION".equals(dateStatus)
                    || "VARIES".equals(dateStatus)) {
                continue;
            }

            if ("ALWAYS".equals(dateStatus)) {
                if (!fallbackUsed) {
                    alwaysCount++;
                }
                continue;
            }

            // 월 단위 등 날짜가 불명확한 경우 제외
            if (!"DAY".equalsIgnoreCase(benefit.datePrecision())) {
                continue;
            }

            addEvent(
                    events,
                    benefit,
                    benefit.applyStart(),
                    "APPLY_START",
                    startDate,
                    endDate
            );

            addEvent(
                    events,
                    benefit,
                    benefit.applyEnd(),
                    "APPLY_END",
                    startDate,
                    endDate
            );

            addEvent(
                    events,
                    benefit,
                    benefit.resultDate(),
                    "RESULT",
                    startDate,
                    endDate
            );
        }

        events.sort(
                Comparator.comparing(CalendarEvent::date)
                        .thenComparing(CalendarEvent::benefitId)
                        .thenComparing(CalendarEvent::eventType)
        );

        return new HomeCalendarResponse(
                year,
                month,
                events,
                alwaysCount,
                fallbackUsed
        );
    }

    /**
     * 조회 월에 포함된 날짜만 일정에 추가한다.
     */
    private void addEvent(
            List<CalendarEvent> events,
            CalendarBenefitRow benefit,
            LocalDate date,
            String eventType,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (date == null) {
            return;
        }

        if (date.isBefore(startDate) || date.isAfter(endDate)) {
            return;
        }

        events.add(new CalendarEvent(
                benefit.benefitId(),
                benefit.title(),
                date,
                eventType
        ));
    }
}