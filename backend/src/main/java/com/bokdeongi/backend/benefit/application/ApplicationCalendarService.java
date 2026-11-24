package com.bokdeongi.backend.benefit.application;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class ApplicationCalendarService {

    private static final DateTimeFormatter ICS_DATE =
            DateTimeFormatter.BASIC_ISO_DATE;

    private static final DateTimeFormatter ICS_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                    .withZone(ZoneOffset.UTC);

    private final ApplicationQuery query;
    private final BenefitRepository benefitRepository;

    public ApplicationCalendarService(
            ApplicationQuery query,
            BenefitRepository benefitRepository
    ) {
        this.query = query;
        this.benefitRepository = benefitRepository;
    }

    @Transactional(readOnly = true)
    public String createCalendar(long userId, long applicationId) {

        // 신청 건 또는 관심 혜택 조회
        ApplicationQuery.ApplicationRow row =
                query.findForCalendar(applicationId, userId)
                        .orElseThrow(
                                ApplicationCalendarException::applicationNotFound
                        );

        // 연결된 혜택 조회
        Benefit benefit = benefitRepository
                .findById(row.benefitId())
                .orElseThrow(
                        ApplicationCalendarException::benefitNotFound
                );

        LocalDate deadline = validateDeadline(benefit);

        String title = Optional.ofNullable(benefit.getTitle())
                .filter(value -> !value.isBlank())
                .orElse("혜택");

        return buildCalendar(
                applicationId,
                title,
                deadline
        );
    }

    // 공통 캘린더 등록 가능 여부 검증
    private LocalDate validateDeadline(Benefit benefit) {

        CalendarAvailability availability =
                CalendarAvailability.evaluate(
                        benefit.getDateStatus(),
                        benefit.getDatePrecision(),
                        benefit.getApplyEnd()
                );

        if (!availability.available()) {
            throw ApplicationCalendarException.unavailable(
                    availability.unavailableReason()
            );
        }

        return benefit.getApplyEnd();
    }

    // iCalendar 형식의 문자열 생성
    private String buildCalendar(
            long applicationId,
            String benefitName,
            LocalDate deadline
    ) {
        String uid = "application-" + applicationId
                + "@bokdeongi";

        String timestamp = ICS_TIMESTAMP.format(Instant.now());

        StringBuilder ics = new StringBuilder();

        appendLine(ics, "BEGIN:VCALENDAR");
        appendLine(ics, "VERSION:2.0");
        appendLine(ics, "PRODID:-//Bokdeongi//Application Calendar//KO");
        appendLine(ics, "CALSCALE:GREGORIAN");
        appendLine(ics, "METHOD:PUBLISH");

        appendLine(ics, "BEGIN:VEVENT");
        appendLine(ics, "UID:" + uid);
        appendLine(ics, "DTSTAMP:" + timestamp);

        // 종일 일정: 종료일은 다음 날로 설정
        appendLine(ics, "DTSTART;VALUE=DATE:"
                + ICS_DATE.format(deadline));

        appendLine(ics, "DTEND;VALUE=DATE:"
                + ICS_DATE.format(deadline.plusDays(1)));

        appendLine(ics, "SUMMARY:"
                + escapeText(benefitName + " 신청 마감"));

        appendLine(ics, "DESCRIPTION:"
                + escapeText("복덩이 혜택 신청 마감일입니다."));

        // 마감 하루 전 알림
        appendLine(ics, "BEGIN:VALARM");
        appendLine(ics, "ACTION:DISPLAY");
        appendLine(ics, "DESCRIPTION:"
                + escapeText(benefitName + " 신청 마감 하루 전"));
        appendLine(ics, "TRIGGER:-P1D");
        appendLine(ics, "END:VALARM");

        appendLine(ics, "END:VEVENT");
        appendLine(ics, "END:VCALENDAR");

        return ics.toString();
    }

    // iCalendar 텍스트 특수문자 처리
    private String escapeText(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "\\n")
                .replace(",", "\\,")
                .replace(";", "\\;");
    }

    // UTF-8 기준 75바이트 이하로 줄 접기
    private void appendLine(StringBuilder builder, String line) {
        int bytes = 0;

        for (int offset = 0; offset < line.length();) {
            int codePoint = line.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int length = character.getBytes(StandardCharsets.UTF_8).length;

            if (bytes + length > 75) {
                builder.append("\r\n ");
                bytes = 1;
            }

            builder.append(character);
            bytes += length;
            offset += Character.charCount(codePoint);
        }

        builder.append("\r\n");
    }
}