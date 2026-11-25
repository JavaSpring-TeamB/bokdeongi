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
                        "PARSED", "DAY", DEADLINE
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
                        "PARSED", "MONTH", DEADLINE
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
                        "parsed", "day", DEADLINE
                );

        assertTrue(result.available());
        assertNull(result.unavailableReason());
    }

    /**
     * 파싱되지 않은 마감일은 날짜 값이 존재해도
     * 캘린더에 등록할 수 없다.
     */
    @Test
    void shouldRejectUnparsedDeadline() {
        CalendarAvailability result =
                CalendarAvailability.evaluate(
                        "UNKNOWN", "DAY", DEADLINE
                );

        assertFalse(result.available());
        assertEquals(
                "정확한 신청 마감일을 확인할 수 없습니다.",
                result.unavailableReason()
        );
    }

    /**
     * 날짜 상태가 없으면 캘린더에 등록할 수 없다.
     */
    @Test
    void shouldRejectNullDateStatus() {
        CalendarAvailability result =
                CalendarAvailability.evaluate(
                        null, "DAY", DEADLINE
                );

        assertFalse(result.available());
        assertNotNull(result.unavailableReason());
    }

    /**
     * 정상 파싱 상태라도 실제 마감일이 없으면
     * 캘린더에 등록할 수 없다.
     */
    @Test
    void shouldRejectParsedStatusWithoutDeadline() {
        CalendarAvailability result =
                CalendarAvailability.evaluate(
                        "PARSED", "DAY", null
                );

        assertFalse(result.available());
        assertEquals(
                "정확한 신청 마감일을 확인할 수 없습니다.",
                result.unavailableReason()
        );
    }
}