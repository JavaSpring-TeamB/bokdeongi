package com.bokdeongi.backend.home.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * HOME-03 홈 신청 일정 캘린더 응답 DTO.
 *
 * 로그인 사용자의 관심 및 진행 중 혜택에 대한
 * 월별 신청 일정을 홈 캘린더 위젯에 제공한다.
 */
public record HomeCalendarResponse(
        int year,
        int month,
        List<CalendarEvent> events,
        int alwaysCount,
        boolean fallbackUsed
) {

    /**
     * 캘린더에 표시할 개별 혜택 일정.
     *
     * eventType:
     * APPLY_START - 접수 시작
     * APPLY_END   - 신청 마감
     * RESULT      - 결과 발표
     */
    public record CalendarEvent(
            Long benefitId,
            String title,
            LocalDate date,
            String eventType
    ) {
    }
}