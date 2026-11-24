package com.bokdeongi.backend.benefit.application;

import java.time.LocalDate;

public record CalendarAvailability(
        boolean available,
        String unavailableReason
) {

    public static CalendarAvailability evaluate(
            String dateStatus,
            String datePrecision,
            LocalDate applyEnd
    ) {

        if ("ALWAYS".equalsIgnoreCase(dateStatus)) {
            return unavailable(
                    "상시 신청 혜택은 마감일이 없습니다."
            );
        }

        if ("VARIES".equalsIgnoreCase(dateStatus)) {
            return unavailable(
                    "접수기관별로 신청 일정이 다릅니다."
            );
        }

        if ("NO_APPLICATION".equalsIgnoreCase(dateStatus)) {
            return unavailable(
                    "별도 신청이 필요 없는 혜택입니다."
            );
        }

        if (applyEnd == null) {
            return unavailable(
                    "정확한 신청 마감일을 확인할 수 없습니다."
            );
        }

        if (!"DAY".equalsIgnoreCase(datePrecision)) {
            return unavailable(
                    "신청 마감일이 일 단위로 확정되지 않았습니다."
            );
        }

        return new CalendarAvailability(true, null);
    }

    private static CalendarAvailability unavailable(
            String reason
    ) {
        return new CalendarAvailability(false, reason);
    }
}