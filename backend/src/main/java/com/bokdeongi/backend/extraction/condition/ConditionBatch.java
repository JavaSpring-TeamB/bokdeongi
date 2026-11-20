package com.bokdeongi.backend.extraction.condition;

import java.util.List;

/**
 * LLM 응답 모양 (data-lab/conditions/output_format.json). 여러 공고를 한 번에 물어 공고마다 items 하나.
 *
 * @param items 공고별 결과. 들어온 순서대로 오지만 serviceId로 맞춘다
 */
public record ConditionBatch(List<Item> items) {

    public ConditionBatch {
        items = items == null ? List.of() : items;
    }

    /** 공고 하나의 조건들. */
    public record Item(String serviceId, List<Condition> conditions) {

        public Item {
            conditions = conditions == null ? List.of() : conditions;
        }
    }

    /**
     * 조건 한 줄 (benefit_rules 한 행 모양).
     *
     * @param group       0 = 공통, 1 이상 = '또는' 묶음 (D-62)
     * @param field       판정 필드, ANSWER:{key}, UNPARSED, NONE, OUT_OF_SCOPE
     * @param operator    EQ / IN / LTE / GTE / BETWEEN. 특수 줄은 비어 있음
     * @param values      문자열 배열 (숫자도 문자열)
     * @param sourceQuote 원문에서 글자 그대로 복사한 근거
     * @param question    ANSWER일 때 사용자에게 물을 질문
     */
    public record Condition(Integer group, String field, String operator, List<String> values, String sourceQuote,
            String question) {

        public int groupOrZero() {
            return group == null || group < 0 ? 0 : group;
        }
    }
}
