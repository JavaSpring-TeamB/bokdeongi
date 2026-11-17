package com.bokdeongi.backend.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 검증 모듈 테스트 (D-58).
 *
 * <p>앞부분은 Python판(test_validator.py)과 같은 공용 벡터 data-lab/validation/cases.json을 읽는다.
 * 두 구현이 같은 기대값을 통과해야 한다. 뒷부분은 Python판의 경계값 테스트를 그대로 옮긴 것이다.
 */
class ExtractionValidatorTest {

    /** Gradle은 backend/에서 테스트를 돌리므로 레포 루트 기준 상대 경로. */
    private static final Path CASES = Path.of("..", "data-lab", "validation", "cases.json");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cases() {
        // A 브랜치처럼 data-lab/validation이 아직 없는 브랜치에서는 건너뛴다. dev에는 항상 있다
        Assumptions.assumeTrue(Files.exists(CASES), "공용 테스트 벡터가 이 브랜치에 없음: " + CASES);
        try {
            return (Map<String, Object>) MiniJson.parse(Files.readString(CASES, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("공용 테스트 벡터가 없음: " + CASES.toAbsolutePath(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> section(String name) {
        return (List<Map<String, Object>>) cases().get(name);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object list) {
        if (list == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : (List<Object>) list) {
            out.add(o instanceof BigDecimal d ? d.toPlainString() : (String) o);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Spec spec(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof List<?> list) {   // consistency: [field, operator, values...]
            List<String> parts = strings(list);
            return new Spec(parts.get(0), parts.get(1), parts.subList(2, parts.size()));
        }
        Map<String, Object> m = (Map<String, Object>) data;
        return new Spec((String) m.get("field"), (String) m.get("operator"), strings(m.get("values")));
    }

    @SuppressWarnings("unchecked")
    private static Item item(Object data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> m = (Map<String, Object>) data;
        return new Item((String) m.get("field"), (String) m.get("source_quote"), strings(m.get("numbers")),
                strings(m.get("dates")), spec(m.get("rule")), (String) m.get("value"), (String) m.get("text"));
    }

    private static Set<BigDecimal> canonSet(List<String> values) {
        Set<BigDecimal> out = new HashSet<>();
        for (String v : values) {
            out.add(ExtractionValidator.canon(v));
        }
        return out;
    }

    // ---------------------------------------------------------------- 공용 벡터

    @Test
    @DisplayName("공용 벡터: 숫자 읽기")
    void sharedNumbers() {
        for (Map<String, Object> c : section("numbers")) {
            String text = (String) c.get("text");
            assertEquals(canonSet(strings(c.get("expected"))), ExtractionValidator.extractNumbers(text), text);
        }
    }

    @Test
    @DisplayName("공용 벡터: 날짜 읽기")
    @SuppressWarnings("unchecked")
    void sharedDates() {
        for (Map<String, Object> c : section("dates")) {
            String text = (String) c.get("text");
            List<List<Object>> expected = new ArrayList<>();
            for (Object row : (List<Object>) c.get("expected")) {
                List<Object> r = (List<Object>) row;
                expected.add(java.util.Arrays.asList(
                        r.get(0) == null ? null : ((BigDecimal) r.get(0)).intValueExact(),
                        ((BigDecimal) r.get(1)).intValueExact(),
                        r.get(2) == null ? null : ((BigDecimal) r.get(2)).intValueExact(),
                        r.get(3)));
            }
            List<List<Object>> got = new ArrayList<>();
            for (DateMention m : ExtractionValidator.extractDates(text)) {
                got.add(java.util.Arrays.asList(m.year(), m.month(), m.day(), m.explicit()));
            }
            assertEquals(expected, got, text);
        }
    }

    @Test
    @DisplayName("공용 벡터: 코드와 모순 여부")
    void sharedConsistency() {
        for (Map<String, Object> c : section("consistency")) {
            Spec llm = spec(c.get("llm"));
            Spec code = spec(c.get("code"));
            assertEquals(c.get("expected"), ExtractionValidator.consistent(llm, code), llm + " / " + code);
        }
    }

    @Test
    @DisplayName("공용 벡터: 근거 문구 찾기")
    void sharedQuote() {
        for (Map<String, Object> c : section("quote")) {
            String quote = (String) c.get("quote");
            assertEquals(c.get("expected"), ExtractionValidator.quoteFound(quote, strings(c.get("sources"))), quote);
        }
    }

    @Test
    @DisplayName("공용 벡터: 검증 결과(버림·점수·검사별 결과)")
    @SuppressWarnings("unchecked")
    void sharedValidate() {
        for (Map<String, Object> c : section("validate")) {
            String name = (String) c.get("name");
            Object baseYear = c.get("base_year");
            Validation result = ExtractionValidator.validate(item(c.get("item")), Purpose.valueOf((String) c.get("purpose")),
                    strings(c.get("sources")), spec(c.get("code_rule")), item(c.get("repeat")),
                    baseYear == null ? null : ((BigDecimal) baseYear).intValueExact());
            Map<String, Object> expected = (Map<String, Object>) c.get("expected");
            assertEquals(expected.get("dropped"), result.dropped(), name + " / dropped");
            assertEquals(0, new BigDecimal((String) expected.get("score")).compareTo(result.score()), name + " / score");
            assertEquals(new LinkedHashMap<>((Map<String, Object>) expected.get("checks")), new LinkedHashMap<>(result.checks()),
                    name + " / checks");
            if (result.dropped()) {
                assertNotNull(result.reason());
            }
        }
    }

    // ---------------------------------------------------------------- Python판 경계값 테스트

    @Test
    @DisplayName("공백·줄바꿈·글머리 기호만 지운다")
    void normalizeRemovesOnlySpacingAndBullets() {
        assertEquals("(대상)만19~34세무주택자", ExtractionValidator.normalize("ㅇ (대상) 만 19～34세\n  - 무주택자"));
        assertEquals("", ExtractionValidator.normalize(null));
        assertEquals("", ExtractionValidator.normalize("  \n "));
    }

    @Test
    @DisplayName("비슷한 글자로 바꿔 주지 않는다: 오탈자가 있으면 근거 불일치")
    void keepsLetters() {
        assertFalse(ExtractionValidator.quoteFound("무주택 세대주", List.of("무주택세대원")));
    }

    @Test
    @DisplayName("다른 필드끼리 모순 비교를 하면 오류")
    void consistencyRejectsDifferentFields() {
        assertThrows(IllegalArgumentException.class,
                () -> ExtractionValidator.consistent(Spec.of("AGE", "LTE", "34"), Spec.of("INCOME_PCT", "LTE", "60")));
    }

    @Test
    @DisplayName("조건 없이 코드와 비교하면 코드 검사 실패")
    void ruleItemWithoutRuleFailsCodeCheck() {
        Item item = new Item("AGE", "만 19~34세", List.of("19", "34"), List.of(), null, null, null);
        Validation r = ExtractionValidator.validate(item, Purpose.RULE, List.of("만 19~34세"),
                Spec.of("AGE", "BETWEEN", "19", "34"), null, null);
        assertEquals(Boolean.FALSE, r.checks().get("code"));
    }

    @Test
    @DisplayName("글머리 하이픈은 음수가 아니다, 3.5%는 날짜가 아니라 소수")
    void bulletsAndDecimals() {
        assertTrue(ExtractionValidator.extractNumbers("- 소득 기준 없음").isEmpty());
        assertEquals(Set.of(new BigDecimal("3.5")), ExtractionValidator.extractNumbers("3.5%"));
        assertTrue(ExtractionValidator.extractDates("3.5%").isEmpty());
    }

    @Test
    @DisplayName("값이 월까지만이면 일이 있는 근거와 맞는다, 원문에 없는 일을 지어내면 버린다")
    void monthAndDayPrecision() {
        Item monthOnly = new Item("apply_start", "2026.04.27~2026.05.29", List.of(), List.of("2026-04"), null, null, null);
        assertFalse(ExtractionValidator.validate(monthOnly, Purpose.EXTRACT, List.of("2026.04.27~2026.05.29")).dropped());
        Item invented = new Item("apply_start", "2026. 4. ~ 2026. 11.", List.of(), List.of("2026-04-01"), null, null, null);
        assertTrue(ExtractionValidator.validate(invented, Purpose.EXTRACT, List.of("2026. 4. ~ 2026. 11.")).dropped());
    }

    @Test
    @DisplayName("연도가 원문에 없으면 추정값 메모")
    void yearInferredNote() {
        Item item = new Item("apply_start", "매년 01-01~01-31", List.of(), List.of("2027-01-01"), null, null, null);
        Validation r = ExtractionValidator.validate(item, Purpose.EXTRACT, List.of("매년 01-01~01-31"));
        assertTrue(r.notes().stream().anyMatch(n -> n.contains("추정")));
    }

    @Test
    @DisplayName("숫자가 없어도 근거 문구는 필수")
    void quoteRequired() {
        assertTrue(ExtractionValidator.validate(Item.empty("exclusion_text"), Purpose.EXTRACT, List.of("아무 원문")).dropped());
    }

    @Test
    @DisplayName("숫자는 원문 전체가 아니라 근거 문구 안에서 찾는다")
    void numbersCheckedAgainstQuote() {
        Item item = new Item("AGE", "청년", List.of("34"), List.of(), null, null, null);
        assertTrue(ExtractionValidator.validate(item, Purpose.RULE, List.of("만 19~34세 이하 청년")).dropped());
    }

    @Test
    @DisplayName("미만·초과는 정수 경계로 바꾼 값도 통과")
    void lessThanBoundary() {
        Item item = new Item("AGE", "만 15세 이상 35세 미만", List.of("15", "34"), List.of(), null, null, null);
        Validation r = ExtractionValidator.validate(item, Purpose.RULE, List.of("○ 만 15세 이상 35세 미만의 미취업자"));
        assertFalse(r.dropped());
        assertTrue(r.notes().stream().anyMatch(n -> n.contains("정수 경계")));
    }
}
