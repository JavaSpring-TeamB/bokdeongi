package com.bokdeongi.backend.extraction;

/** 검증 용도. 용도마다 필수 검사가 다르다. */
public enum Purpose {
    /** 판정 조건 (DATA-03). */
    RULE,
    /** 날짜·금액·중복 불가 (DATA-04~06). */
    EXTRACT,
    /** 쉬운 말 (EASY-01·02): 문장 속 숫자가 모두 원문에 있어야 한다. */
    EASY
}
