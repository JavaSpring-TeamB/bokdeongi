package com.bokdeongi.backend.extraction;

import java.util.List;

/**
 * 검증할 LLM 출력 하나.
 *
 * @param field       필드 이름 (AGE, max_amount, apply_end ...)
 * @param sourceQuote LLM이 가리킨 근거 문구
 * @param numbers     근거 문구 안에 있어야 하는 숫자 (나이 [19, 34], 금액 [200000]). 계산해서 만든 값(연간 환산)은 넣지 않는다
 * @param dates       근거 문구 안에 있어야 하는 날짜 ('2026-03-02' 또는 '2026-03')
 * @param rule        RULE의 조건 (코드 모순·반복 비교에 씀)
 * @param value       EXTRACT의 값 (반복 비교에 씀, 예: "200000")
 * @param text        EASY에서 검사할 문장 전체
 */
public record Item(String field, String sourceQuote, List<String> numbers, List<String> dates, Spec rule,
        String value, String text) {

    public Item {
        numbers = numbers == null ? List.of() : List.copyOf(numbers);
        dates = dates == null ? List.of() : List.copyOf(dates);
    }

    /** 빈 출력 (반복 실행에서 그 필드가 없을 때). */
    public static Item empty(String field) {
        return new Item(field, null, List.of(), List.of(), null, null, null);
    }

    public Object key() {
        return rule != null ? rule.key() : value;
    }
}
