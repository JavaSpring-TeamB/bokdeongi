package com.bokdeongi.eligibility;

/** 조건 결과가 UNKNOWN인 이유. 화면에서 "왜 조건부인지"와 추가 질문(BEN-04) 대상을 정할 때 쓴다. */
public enum UnknownReason {
    /** 회원이 해당 항목을 건너뛰었다 (D-45). 추가 질문으로 해소할 수 있다. */
    MISSING_VALUE,
    /** 소득이 구간 경계 ±5%p 안이다 (D-54). 기관 확인이 필요하다. */
    BOUNDARY,
    /** 민감 항목인데 민감정보 동의가 없거나 건너뛰었다. 동의 안내로 해소한다. */
    SENSITIVE_UNKNOWN,
    /** LLM이 뽑은 규칙의 신뢰도가 낮다. */
    LOW_CONFIDENCE,
    /** 회원 정보로는 판단할 수 없는 조건이다 (예: 소상공인 업종). 기관 확인이 필요하다. */
    NEEDS_CHECK,
    /** 규칙 형식을 해석하지 못했다. 원문 확인이 필요하다. */
    UNSUPPORTED_RULE
}
