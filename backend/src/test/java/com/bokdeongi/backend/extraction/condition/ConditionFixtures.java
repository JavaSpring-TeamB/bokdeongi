package com.bokdeongi.backend.extraction.condition;

import com.bokdeongi.backend.eligibility.Operator;
import com.bokdeongi.backend.eligibility.Rule;

import java.time.LocalDateTime;
import java.util.List;

/** 조건 추출 테스트용 공고·조건. */
final class ConditionFixtures {

    private ConditionFixtures() {
    }

    /** 천안 인플루엔자 예방접종 (라벨 README 예시) */
    static ConditionSource cheonan() {
        return new ConditionSource(10L, "449000000119", "인플루엔자 예방접종", "충청남도 천안시",
                "60~64세 주민 / 14세~59세 기초생활수급자 · 장애의 정도가 심한 장애인 / 14세~59세 국가유공자",
                null, "인플루엔자 무료 접종", "보건소 방문", LocalDateTime.of(2026, 10, 28, 1, 0),
                List.of(Rule.code("AGE", Operator.BETWEEN, List.of("14", "64"), "지원조건: 만 14세 ~ 64세"),
                        new Rule(null, "JA_GROUP:JA03", Operator.IN, List.of("JA0328", "JA0329"), "CODE",
                                "지원조건: 장애인, 국가보훈대상자", null, 0)),
                false);
    }

    static ConditionBatch.Condition c(int group, String field, String op, List<String> values, String quote) {
        return new ConditionBatch.Condition(group, field, op, values, quote, null);
    }

    static ConditionBatch.Condition answer(int group, String key, String value, String quote, String question) {
        return new ConditionBatch.Condition(group, "ANSWER:" + key, "EQ", List.of(value), quote, question);
    }

    static List<ConditionBatch.Condition> cheonanConditions() {
        return List.of(
                c(1, "AGE", "BETWEEN", List.of("60", "64"), "60~64세 주민"),
                c(2, "AGE", "BETWEEN", List.of("14", "59"), "14세~59세 기초생활수급자"),
                c(2, "WELFARE", "IN", List.of("BASIC"), "14세~59세 기초생활수급자"),
                c(3, "SENSITIVE", "IN", List.of("JA0328"), "장애의 정도가 심한 장애인"),
                answer(3, "severe_disability", "Y", "장애의 정도가 심한 장애인", "장애의 정도가 심한 장애인인가요?"),
                c(4, "SENSITIVE", "IN", List.of("JA0329"), "14세~59세 국가유공자"));
    }
}
