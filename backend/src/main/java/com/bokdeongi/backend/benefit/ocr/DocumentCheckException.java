package com.bokdeongi.backend.benefit.ocr;

/**
 * 서류 검토(ASST-03) 실패. 코드는 노션 API 명세 '에러 코드' 표를 따른다.
 * DocumentCheckExceptionHandler가 ApiErrorResponse로 바꾼다.
 */
public class DocumentCheckException extends RuntimeException {

    private final int status;
    private final String code;

    public DocumentCheckException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** 서류 처리 동의(DOC_PROCESS, D-76)가 없음 */
    public static DocumentCheckException consentRequired() {
        return new DocumentCheckException(403, "CONSENT_REQUIRED", "서류 처리에 동의해야 서류를 올릴 수 있어요.");
    }

    public static DocumentCheckException fileTooLarge() {
        return new DocumentCheckException(413, "FILE_TOO_LARGE", "10MB 이하 파일만 올릴 수 있어요.");
    }

    public static DocumentCheckException unsupportedFormat() {
        return new DocumentCheckException(415, "UNSUPPORTED_FORMAT", "JPG, PNG, PDF만 올릴 수 있어요.");
    }

    public static DocumentCheckException invalidInput(String message) {
        return new DocumentCheckException(400, "INVALID_INPUT", message);
    }

    public static DocumentCheckException notFound(String message) {
        return new DocumentCheckException(404, "NOT_FOUND", message);
    }

    /** OCR 서버가 꺼져 있거나 응답이 이상할 때. 프론트는 "직접 체크" 버튼으로 안내한다 */
    public static DocumentCheckException unavailable() {
        return new DocumentCheckException(502, "AI_UNAVAILABLE",
                "지금은 서류 사진을 확인할 수 없어요. 잠시 후 다시 올리거나 직접 체크해 주세요.");
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
