package com.bokdeongi.eligibility;

/**
 * 조건 하나의 판정 근거 (eligibility_results.trace의 원소, 원칙 2).
 *
 * @param ruleId      규칙 ID
 * @param field       판정 필드
 * @param expected    공고가 요구하는 값 (사람이 읽는 형태)
 * @param actual      회원 값 (민감 항목은 "비공개")
 * @param outcome     결과
 * @param reason      UNKNOWN일 때 이유, 아니면 null
 * @param sourceQuote 근거 원문 문구
 * @param boundary    경계값이라 조건부가 됐는지
 * @param group       조건 묶음 (D-62). 화면에서 "다음 중 하나" 묶음을 보여 줄 때 쓴다
 */
public record ConditionTrace(
        Long ruleId,
        String field,
        String expected,
        String actual,
        Outcome outcome,
        UnknownReason reason,
        String sourceQuote,
        boolean boundary,
        int group) {

    /** 공통 조건(묶음 0)의 근거. */
    public ConditionTrace(Long ruleId, String field, String expected, String actual, Outcome outcome,
            UnknownReason reason, String sourceQuote, boolean boundary) {
        this(ruleId, field, expected, actual, outcome, reason, sourceQuote, boundary, 0);
    }
}
