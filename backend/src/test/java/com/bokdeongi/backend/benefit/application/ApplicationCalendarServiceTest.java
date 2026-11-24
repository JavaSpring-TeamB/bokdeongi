package com.bokdeongi.backend.benefit.application;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationCalendarServiceTest {

    private ApplicationQuery query;
    private BenefitRepository benefitRepository;
    private ApplicationCalendarService service;

    private static final long USER_ID = 1L;
    private static final long APPLICATION_ID = 260L;
    private static final long BENEFIT_ID = 1361L;

    @BeforeEach
    void setUp() {
        query = mock(ApplicationQuery.class);
        benefitRepository = mock(BenefitRepository.class);

        service = new ApplicationCalendarService(
                query,
                benefitRepository
        );
    }

    private Benefit benefit(
            LocalDate deadline,
            String dateStatus,
            String datePrecision
    ) {
        Benefit benefit = mock(Benefit.class);

        when(benefit.getApplyEnd()).thenReturn(deadline);
        when(benefit.getDateStatus()).thenReturn(dateStatus);
        when(benefit.getDatePrecision()).thenReturn(datePrecision);
        when(benefit.getTitle()).thenReturn("청년 지원 사업");

        return benefit;
    }

    private void mockApplication(Benefit benefit) {
        ApplicationQuery.ApplicationRow row =
                new ApplicationQuery.ApplicationRow(
                        APPLICATION_ID,
                        USER_ID,
                        BENEFIT_ID,
                        "IN_PROGRESS",
                        1,
                        null,
                        null,
                        null
                );

        when(query.findForCalendar(APPLICATION_ID, USER_ID))
                .thenReturn(Optional.of(row));

        when(benefitRepository.findById(BENEFIT_ID))
                .thenReturn(Optional.of(benefit));
    }

    @Test
    void shouldCreateCalendarWithOneDayReminder() {
        Benefit benefit = benefit(
                LocalDate.of(2026, 12, 10),
                "OPEN",
                "DAY"
        );

        mockApplication(benefit);

        String ics = service.createCalendar(
                USER_ID,
                APPLICATION_ID
        );

        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("BEGIN:VEVENT"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261210"));
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261211"));
        assertTrue(ics.contains("BEGIN:VALARM"));
        assertTrue(ics.contains("TRIGGER:-P1D"));
        assertTrue(ics.contains("ACTION:DISPLAY"));
        assertTrue(ics.contains("END:VCALENDAR"));
    }

    @Test
    void shouldRejectMissingDeadline() {
        Benefit benefit = benefit(
                null,
                "UNKNOWN",
                "NONE"
        );

        mockApplication(benefit);

        ApplicationCalendarException exception = assertThrows(
                ApplicationCalendarException.class,
                () -> service.createCalendar(
                        USER_ID,
                        APPLICATION_ID
                )
        );

        assertEquals(400, exception.getStatus().value());
        assertEquals("CALENDAR_UNAVAILABLE", exception.getCode());
        assertEquals(
                "정확한 신청 마감일을 확인할 수 없습니다.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectAlwaysApplication() {
        Benefit benefit = benefit(
                null,
                "ALWAYS",
                "NONE"
        );

        mockApplication(benefit);

        ApplicationCalendarException exception = assertThrows(
                ApplicationCalendarException.class,
                () -> service.createCalendar(
                        USER_ID,
                        APPLICATION_ID
                )
        );

        assertEquals(400, exception.getStatus().value());
        assertEquals("CALENDAR_UNAVAILABLE", exception.getCode());
        assertTrue(exception.getMessage().contains("상시 신청"));
    }

    @Test
    void shouldRejectVariesApplication() {
        Benefit benefit = benefit(
                null,
                "VARIES",
                "NONE"
        );

        mockApplication(benefit);

        ApplicationCalendarException exception = assertThrows(
                ApplicationCalendarException.class,
                () -> service.createCalendar(
                        USER_ID,
                        APPLICATION_ID
                )
        );

        assertEquals(400, exception.getStatus().value());
        assertEquals("CALENDAR_UNAVAILABLE", exception.getCode());
        assertEquals(
                "접수기관별로 신청 일정이 다릅니다.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNoApplicationRequired() {
        Benefit benefit = benefit(
                null,
                "NO_APPLICATION",
                "NONE"
        );

        mockApplication(benefit);

        ApplicationCalendarException exception = assertThrows(
                ApplicationCalendarException.class,
                () -> service.createCalendar(
                        USER_ID,
                        APPLICATION_ID
                )
        );

        assertEquals(400, exception.getStatus().value());
        assertEquals("CALENDAR_UNAVAILABLE", exception.getCode());
        assertEquals(
                "별도 신청이 필요 없는 혜택입니다.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectImpreciseDeadline() {
        Benefit benefit = benefit(
                LocalDate.of(2026, 12, 10),
                "OPEN",
                "MONTH"
        );

        mockApplication(benefit);

        ApplicationCalendarException exception = assertThrows(
                ApplicationCalendarException.class,
                () -> service.createCalendar(
                        USER_ID,
                        APPLICATION_ID
                )
        );

        assertEquals(400, exception.getStatus().value());
        assertEquals("CALENDAR_UNAVAILABLE", exception.getCode());
        assertEquals(
                "신청 마감일이 일 단위로 확정되지 않았습니다.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectOtherUsersApplication() {
        when(query.findForCalendar(APPLICATION_ID, USER_ID))
                .thenReturn(Optional.empty());

        ApplicationCalendarException exception = assertThrows(
                ApplicationCalendarException.class,
                () -> service.createCalendar(
                        USER_ID,
                        APPLICATION_ID
                )
        );

        assertEquals(404, exception.getStatus().value());
        assertEquals("APPLICATION_NOT_FOUND", exception.getCode());

        verifyNoInteractions(benefitRepository);
    }

    @Test
    void shouldSupportFavoriteWithoutApplicationStatus() {
        ApplicationQuery.ApplicationRow favorite =
                new ApplicationQuery.ApplicationRow(
                        APPLICATION_ID,
                        USER_ID,
                        BENEFIT_ID,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        when(query.findForCalendar(APPLICATION_ID, USER_ID))
                .thenReturn(Optional.of(favorite));

        // Benefit 객체를 먼저 생성
        Benefit favoriteBenefit = benefit(
                LocalDate.of(2026, 12, 10),
                "OPEN",
                "DAY"
        );

        // 생성된 객체로 Mockito 설정
        when(benefitRepository.findById(BENEFIT_ID))
                .thenReturn(Optional.of(favoriteBenefit));

        String ics = service.createCalendar(
                USER_ID,
                APPLICATION_ID
        );

        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261210"));
    }

    @Test
    void shouldRejectMissingBenefit() {
        ApplicationQuery.ApplicationRow row =
                new ApplicationQuery.ApplicationRow(
                        APPLICATION_ID,
                        USER_ID,
                        BENEFIT_ID,
                        "IN_PROGRESS",
                        1,
                        null,
                        null,
                        null
                );

        when(query.findForCalendar(APPLICATION_ID, USER_ID))
                .thenReturn(Optional.of(row));

        when(benefitRepository.findById(BENEFIT_ID))
                .thenReturn(Optional.empty());

        ApplicationCalendarException exception = assertThrows(
                ApplicationCalendarException.class,
                () -> service.createCalendar(
                        USER_ID,
                        APPLICATION_ID
                )
        );

        assertEquals(404, exception.getStatus().value());
        assertEquals("BENEFIT_NOT_FOUND", exception.getCode());
        assertEquals(
                "혜택 정보를 찾을 수 없습니다.",
                exception.getMessage()
        );
    }
}