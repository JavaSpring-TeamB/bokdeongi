package com.bokdeongi.backend.benefit.application;

import java.time.LocalDate;

/**
 * 혜택 신청 마감일의 캘린더 등록 가능 여부를 판단한다.
 *
 * 정상적으로 파싱된 일 단위 마감일만 등록할 수 있으며,
 * 등록할 수 없는 경우 해당 사유를 제공한다.
 */
public record CalendarAvailability(
        boolean available,
        String unavailableReason
) {

    /**
     * 날짜 상태, 정밀도 및 마감일을 기준으로
     * 캘린더 등록 가능 여부를 검증한다.
     */
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

        // 정상적으로 파싱되지 않은 마감일은 등록하지 않는다.
        if (!"PARSED".equalsIgnoreCase(dateStatus)
                || applyEnd == null) {
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

    /**
     * 캘린더 등록 불가 상태와 사유를 반환한다.
     */
    private static CalendarAvailability unavailable(
            String reason
    ) {
        return new CalendarAvailability(false, reason);
    }
}