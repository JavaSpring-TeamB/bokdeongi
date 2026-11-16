package com.bokdeongi.backend.extraction;

import java.util.List;
import java.util.TreeSet;

/** 판정 조건 하나 (benefit_rules의 field·operator·value와 같은 모양). */
public record Spec(String field, String operator, List<String> values) {

    public Spec {
        values = List.copyOf(values);
    }

    public static Spec of(String field, String operator, String... values) {
        return new Spec(field, operator, List.of(values));
    }

    /** 반복 비교용. IN은 순서를 무시하고 숫자는 표준형으로 맞춘다. */
    public List<Object> key() {
        List<String> canon = values.stream().map(ExtractionValidator::canonText).toList();
        if ("IN".equals(operator)) {
            canon = List.copyOf(new TreeSet<>(canon));
        }
        return List.of(field, operator, canon);
    }
}
