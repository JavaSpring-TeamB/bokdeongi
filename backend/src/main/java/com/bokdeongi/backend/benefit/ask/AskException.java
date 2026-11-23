package com.bokdeongi.backend.benefit.ask;

/** 공고문 질문(BN-08) 실패. 코드는 노션 API 명세 '에러 코드' 표를 따른다 */
public class AskException extends RuntimeException {

    private final int status;
    private final String code;

    public AskException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static AskException invalidInput(String message) {
        return new AskException(400, "INVALID_INPUT", message);
    }

    public static AskException notFound() {
        return new AskException(404, "NOT_FOUND", "혜택을 찾을 수 없어요.");
    }

    public static AskException tooMany() {
        return new AskException(429, "TOO_MANY_REQUESTS",
                "오늘 질문은 " + AskLimiter.DAILY_LIMIT + "번까지 할 수 있어요. 내일 다시 물어봐 주세요.");
    }

    /** LLM이 꺼져 있음 (서버 .env에 GEMINI_API_KEY·AI_CHAT_MODEL=google-genai 없음). 코드는 같고 메시지만 다르다 */
    public static AskException disabled() {
        return new AskException(502, "AI_UNAVAILABLE", "AI 기능이 꺼져 있어요 (서버에 AI 키가 설정되지 않음).");
    }

    /** LLM 호출 실패 (한도·시간 초과·네트워크). 실제 예외는 서버 로그에 남긴다. 프론트는 원문을 대신 보여 준다 */
    public static AskException unavailable() {
        return new AskException(502, "AI_UNAVAILABLE", "지금은 질문에 답할 수 없어요. 공고 원문을 확인하거나 잠시 후 다시 물어봐 주세요.");
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
