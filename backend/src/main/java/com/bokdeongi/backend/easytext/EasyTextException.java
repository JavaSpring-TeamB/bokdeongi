package com.bokdeongi.backend.easytext;

/** 쉬운 말 번역기(EASY-02) 실패. 코드는 노션 API 명세 '에러 코드' 표를 따른다 (공고문 질문과 같은 코드) */
public class EasyTextException extends RuntimeException {

    private final int status;
    private final String code;

    public EasyTextException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static EasyTextException invalidInput(String message) {
        return new EasyTextException(400, "INVALID_INPUT", message);
    }

    public static EasyTextException tooMany() {
        return new EasyTextException(429, "TOO_MANY_REQUESTS",
                "오늘은 " + EasyTextLimiter.DAILY_LIMIT + "번까지 바꿀 수 있어요. 내일 다시 해 주세요.");
    }

    /** LLM이 꺼져 있음 (서버 .env에 GEMINI_API_KEY·AI_CHAT_MODEL=google-genai 없음). 코드는 같고 메시지만 다르다 */
    public static EasyTextException disabled() {
        return new EasyTextException(502, "AI_UNAVAILABLE", "AI 기능이 꺼져 있어요 (서버에 AI 키가 설정되지 않음).");
    }

    /** LLM 호출 실패 (한도·시간 초과·네트워크). 실제 예외는 서버 로그에 남긴다 */
    public static EasyTextException unavailable() {
        return new EasyTextException(502, "AI_UNAVAILABLE", "지금은 쉬운 말로 바꿀 수 없어요. 잠시 후 다시 해 주세요.");
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
