package com.bokdeongi.backend.benefit.application;

import org.springframework.http.HttpStatus;

/**
 * 서류 기억 기능에서 발생하는 예외를 관리한다.
 *
 * HTTP 응답 상태와 오류 코드를 포함하며,
 * 동의 미완료, 사용자 미존재 및 서류 종류 미존재
 * 등의 예외 상황을 처리한다.
 */
public class DocumentRecordException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private DocumentRecordException(
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

    public static DocumentRecordException consentRequired() {
        return new DocumentRecordException(
                HttpStatus.FORBIDDEN,
                "CONSENT_REQUIRED",
                "서류 유효기간 기억에 동의해야 합니다."
        );
    }

    public static DocumentRecordException userNotFound() {
        return new DocumentRecordException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "존재하지 않는 사용자입니다."
        );
    }

    public static DocumentRecordException documentTypeNotFound() {
        return new DocumentRecordException(
                HttpStatus.NOT_FOUND,
                "DOCUMENT_TYPE_NOT_FOUND",
                "존재하지 않는 서류 종류입니다."
        );
    }
}