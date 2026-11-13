package com.bokdeongi.eligibility;

/** 조건 하나의 결과. */
public enum Outcome {
    /** 조건을 만족한다. */
    PASS,
    /** 조건을 확실히 만족하지 않는다. */
    FAIL,
    /** 만족 여부를 알 수 없다 (값 없음·경계값·민감정보 미동의 등). */
    UNKNOWN
}
