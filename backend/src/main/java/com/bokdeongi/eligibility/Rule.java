package com.bokdeongi.eligibility;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 판정 규칙 한 줄 (benefit_rules 한 행).
 *
 * @param id          규칙 ID (trace에 남긴다)
 * @param field       판정 필드. 목록은 노션 테이블 정의서 benefit_rules 참고 (예: AGE, JA_GROUP:JA03, ANSWER:no_house)
 * @param operator    연산자
 * @param values      값. BETWEEN은 [최소, 최대], IN은 후보 목록, 나머지는 한 개
 * @param source      CODE / LLM / MANUAL
 * @param sourceQuote 근거 원문 문구 (원칙 2)
 * @param confidence  LLM 신뢰도. CODE·MANUAL은 null
 */
public record Rule(
        Long id,
        String field,
        Operator operator,
        List<String> values,
        String source,
        String sourceQuote,
        BigDecimal confidence) {

    public Rule {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(operator, "operator");
        values = values == null ? List.of() : List.copyOf(values);
    }

    /** 테스트·코드 변환용 간단 생성자 (지원조건 코드에서 나온 규칙). */
    public static Rule code(String field, Operator operator, List<String> values, String sourceQuote) {
        return new Rule(null, field, operator, values, "CODE", sourceQuote, null);
    }

    public boolean fromLlm() {
        return "LLM".equals(source);
    }
}
