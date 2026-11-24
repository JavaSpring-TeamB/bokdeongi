package com.bokdeongi.backend.benefit.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CalendarAvailabilityTest {

    private static final LocalDate DEADLINE =
            LocalDate.of(2026, 12, 10);

    @Test
    void shouldAllowExactDeadline() {
        CalendarAvailability result =
                CalendarAvailability.evaluate(
                        "OPEN", "DAY", DEADLINE
                );

        assertTrue(result.available());
        assertNull(result.unavailableReason());
    }

    @Test
    void shouldRejectAlwaysApplication() {
        CalendarAvailability result =
                CalendarAvailability.evaluate(
                        "ALWAYS", "DAY", DEADLINE
                );

        assertFalse(result.available());
        assertEquals(
                "상시 신청 혜택은 마감일이 없습니다.",
                result.unavailableReason()
        );
    }

    @Test
    void shouldRejectVariesApplication() {
        CalendarAvailability result =
                CalendarAvailability.evaluate(
                        "VARIES", "DAY", DEADLINE
                );

        assertFalse(result.available());
        assertEquals(
                "접수기관별로 신청 일정이 다릅니다.",
                result.unavailableReason()
        );
    }

    @Test
    void shouldRejectNoApplicationRequired() {
        CalendarAvailability result =
                CalendarAvailability.evaluate(
                        "NO_APPLICATION", "DAY", DEADLINE
                );

        assertFalse(result.available());
        assertEquals(
                "별도 신청이 필요 없는 혜택입니다.",
                result.unavailableReason()
        );
    }

    @Test
    void shouldRejectMissingDeadline() {
        CalendarAvailability result =
                CalendarAvailability.evaluate(
                        "UNKNOWN", "NONE", null
                );

        assertFalse(result.available());
        assertEquals(
                "정확한 신청 마감일을 확인할 수 없습니다.",
                result.unavailableReason()
        );
    }

    @Test
    void shouldRejectMonthlyPrecision() {
        CalendarAvailability result =
                CalendarAvailability.evaluate(
                        "OPEN", "MONTH", DEADLINE
                );

        assertFalse(result.available());
        assertEquals(
                "신청 마감일이 일 단위로 확정되지 않았습니다.",
                result.unavailableReason()
        );
    }

    @Test
    void shouldRejectNullPrecision() {
        CalendarAvailability result =
                CalendarAvailability.evaluate(
                        "OPEN", null, DEADLINE
                );

        assertFalse(result.available());
        assertNotNull(result.unavailableReason());
    }

    @Test
    void shouldSupportCaseInsensitiveValues() {
        CalendarAvailability result =
                CalendarAvailability.evaluate(
                        "open", "day", DEADLINE
                );

        assertTrue(result.available());
        assertNull(result.unavailableReason());
    }
}