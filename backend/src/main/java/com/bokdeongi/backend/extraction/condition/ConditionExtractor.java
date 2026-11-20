package com.bokdeongi.backend.extraction.condition;

/** 판정 조건 추출 LLM 호출 한 번. 테스트에서는 가짜로 바꾼다. */
public interface ConditionExtractor {

    /**
     * @param system {@link ConditionPrompt#system()}
     * @param user   {@link ConditionPrompt#user(java.util.List)}
     * @return 응답. 호출·파싱에 실패하면 예외
     */
    ConditionBatch extract(String system, String user);
}
