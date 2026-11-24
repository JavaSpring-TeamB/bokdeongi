package com.bokdeongi.backend.benefit.application;

import org.springframework.http.HttpStatus;

public class ApplicationCalendarException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private ApplicationCalendarException(
            HttpStatus status,
            String code,
            String message
    ) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApplicationCalendarException applicationNotFound() {
        return new ApplicationCalendarException(
                HttpStatus.NOT_FOUND,
                "APPLICATION_NOT_FOUND",
                "신청 또는 관심 혜택 정보를 찾을 수 없습니다."
        );
    }

    public static ApplicationCalendarException benefitNotFound() {
        return new ApplicationCalendarException(
                HttpStatus.NOT_FOUND,
                "BENEFIT_NOT_FOUND",
                "혜택 정보를 찾을 수 없습니다."
        );
    }

    public static ApplicationCalendarException unavailable(
            String message
    ) {
        return new ApplicationCalendarException(
                HttpStatus.BAD_REQUEST,
                "CALENDAR_UNAVAILABLE",
                message
        );
    }
}