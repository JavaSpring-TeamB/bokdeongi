package com.bokdeongi.backend.eligibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bokdeongi.backend.eligibility.SupportConditionConverter.Result;
import com.bokdeongi.backend.eligibility.SupportConditionConverter.TargetScope;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DATA-02 지원조건 코드 변환 (D-48, D-49). */
class SupportConditionConverterTest {

    /** 개인 특성·가구 특성 그룹이 전부 Y이고 나이 제한 없는, 가장 흔한 '조건 없음' 응답. */
    private static Map<String, String> allOpen() {
        Map<String, String> m = new HashMap<>();
        for (List<String> group : List.of(SupportConditionConverter.JA01, SupportConditionConverter.JA02,
                SupportConditionConverter.JA03, SupportConditionConverter.JA04)) {
            group.forEach(code -> m.put(code, "Y"));
        }
        m.put("JA0322", "Y");
        m.put("JA0410", "Y");
        m.put("JA0110", "0");
        m.put("JA0111", "120");
        return m;
    }

    private static Map<String, String> with(Map<String, String> base, String... pairs) {
        Map<String, String> m = new HashMap<>(base);
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    private static Rule only(Result r, String field) {
        List<Rule> found = r.rules().stream().filter(x -> x.field().equals(field)).toList();
        assertEquals(1, found.size(), field + " 규칙 수");
        return found.get(0);
    }

    @Test
    @DisplayName("전부 Y + 나이 0~120 → 규칙 없음, 개인 대상")
    void allOpenMakesNoRules() {
        Result r = SupportConditionConverter.convert(allOpen(), "개인");
        assertTrue(r.rules().isEmpty());
        assertEquals(TargetScope.PERSONAL, r.targetScope());
        assertTrue(r.warnings().isEmpty());
    }

    @Test
    @DisplayName("성별 하나만 Y → GENDER IN, 근거에 코드 이름")
    void gender() {
        Rule rule = only(SupportConditionConverter.convert(with(allOpen(), "JA0101", ""), "개인"), "GENDER");
        assertEquals(Operator.IN, rule.operator());
        assertEquals(List.of("F"), rule.values());
        assertEquals("CODE", rule.source());
        assertEquals("지원조건: 여성", rule.sourceQuote());
    }

    @Test
    @DisplayName("나이: 0~120·빈 값은 규칙 없음, 상한 120이면 이상, 하한 0이면 이하, 그 외 사이")
    void age() {
        assertTrue(SupportConditionConverter.convert(with(allOpen(), "JA0110", "", "JA0111", ""), "개인").rules().isEmpty());
        Rule gte = only(SupportConditionConverter.convert(with(allOpen(), "JA0110", "18"), "개인"), "AGE");
        assertEquals(Operator.GTE, gte.operator());
        assertEquals(List.of("18"), gte.values());
        Rule lte = only(SupportConditionConverter.convert(with(allOpen(), "JA0111", "17"), "개인"), "AGE");
        assertEquals(Operator.LTE, lte.operator());
        Rule between = only(SupportConditionConverter.convert(with(allOpen(), "JA0110", "19", "JA0111", "34"), "개인"), "AGE");
        assertEquals(List.of("19", "34"), between.values());
        assertEquals("지원조건: 만 19~34세", between.sourceQuote());
    }

    @Test
    @DisplayName("나이 하한 > 상한, 숫자 아님 → 규칙 없이 경고")
    void badAge() {
        Result reversed = SupportConditionConverter.convert(with(allOpen(), "JA0110", "40", "JA0111", "30"), "개인");
        assertTrue(reversed.rules().isEmpty());
        assertEquals(1, reversed.warnings().size());
        assertEquals(1, SupportConditionConverter.convert(with(allOpen(), "JA0110", "만19"), "개인").warnings().size());
    }

    @Test
    @DisplayName("소득 구간 일부 Y → INCOME_PCT IN 구간 코드 (엔진이 구간·경계 처리)")
    void incomeBrackets() {
        Rule rule = only(SupportConditionConverter.convert(
                with(allOpen(), "JA0203", "", "JA0204", "", "JA0205", ""), "개인"), "INCOME_PCT");
        assertEquals(List.of("JA0201", "JA0202"), rule.values());
    }

    @Test
    @DisplayName("개인 특성 일부 Y → JA_GROUP:JA03, '해당사항없음' Y면 규칙 없음")
    void traitGroup() {
        Map<String, String> m = allOpen();
        SupportConditionConverter.JA03.forEach(code -> m.put(code, ""));
        m.put("JA0322", "");
        m.put("JA0320", "Y");
        m.put("JA0327", "Y");
        Rule rule = only(SupportConditionConverter.convert(m, "개인"), "JA_GROUP:JA03");
        assertEquals(List.of("JA0320", "JA0327"), rule.values());
        assertEquals("지원조건: 대학생·대학원생, 구직자·실업자", rule.sourceQuote());

        m.put("JA0322", "Y");
        assertTrue(SupportConditionConverter.convert(m, "개인").rules().isEmpty());
    }

    @Test
    @DisplayName("가구 특성 Y가 하나도 없으면 규칙 없음 (특성 코드는 포함 대상이지 제외 조건이 아님)")
    void householdNoY() {
        Map<String, String> m = allOpen();
        SupportConditionConverter.JA04.forEach(code -> m.put(code, ""));
        m.put("JA0410", "");
        assertTrue(SupportConditionConverter.convert(m, "개인").rules().isEmpty());
    }

    @Test
    @DisplayName("변환한 규칙을 엔진에 넣으면 무주택(JA0412)은 has_house=false로 통과 (D-48)")
    void convertedRuleRunsInEngine() {
        Map<String, String> m = allOpen();
        SupportConditionConverter.JA04.forEach(code -> m.put(code, ""));
        m.put("JA0410", "");
        m.put("JA0412", "Y");
        m.put("JA0110", "19");
        m.put("JA0111", "34");
        List<Rule> rules = SupportConditionConverter.convert(m, "개인").rules();
        ProfileSnapshot p = ProfileSnapshot.empty().withBirthDate(LocalDate.of(2000, 5, 1))
                .withHousehold(1, java.util.Set.of(), false);
        EligibilityResult r = new EligibilityJudge().judge(p, rules, LocalDate.of(2026, 11, 16));
        assertEquals(Verdict.ELIGIBLE, r.verdict());
    }

    @Test
    @DisplayName("사용자구분: 개인·가구 없으면 CORP, 소상공인만이면 SMALL_BIZ (D-49)")
    void scopeByUserType() {
        assertEquals(TargetScope.CORP, SupportConditionConverter.convert(allOpen(), "법인/시설/단체").targetScope());
        assertEquals(TargetScope.SMALL_BIZ,
                SupportConditionConverter.convert(allOpen(), "소상공인//법인/시설/단체").targetScope());
        assertEquals(TargetScope.PERSONAL, SupportConditionConverter.convert(allOpen(), "개인//소상공인").targetScope());
        assertEquals(TargetScope.PERSONAL, SupportConditionConverter.convert(allOpen(), "가구||법인/시설/단체").targetScope());
        assertEquals(TargetScope.PERSONAL, SupportConditionConverter.convert(allOpen(), null).targetScope());
    }

    @Test
    @DisplayName("소상공인 코드 Y → JA11 규칙 + SMALL_BIZ, 엔진에서 자영업은 조건부·나머지는 해당 없음")
    void smallBusiness() {
        Result r = SupportConditionConverter.convert(with(allOpen(), "JA1102", "Y"), "개인");
        assertEquals(TargetScope.SMALL_BIZ, r.targetScope());
        Rule rule = only(r, "JA_GROUP:JA11");
        EligibilityJudge judge = new EligibilityJudge();
        LocalDate today = LocalDate.of(2026, 11, 16);
        ProfileSnapshot selfEmployed = ProfileSnapshot.empty().withStatus(java.util.Set.of(ProfileSnapshot.SELF_EMPLOYED), null);
        ProfileSnapshot worker = ProfileSnapshot.empty().withStatus(java.util.Set.of("JA0326"), null);
        assertEquals(Verdict.CONDITIONAL, judge.judge(selfEmployed, List.of(rule), today).verdict());
        assertEquals(Verdict.INELIGIBLE, judge.judge(worker, List.of(rule), today).verdict());
    }

    @Test
    @DisplayName("개인 대상이어도 개인 특성 Y가 하나도 없고 기관 조건만 있으면 CORP")
    void orgOnlyCodes() {
        Map<String, String> m = new HashMap<>();
        m.put("JA2101", "Y");
        assertEquals(TargetScope.CORP, SupportConditionConverter.convert(m, "개인").targetScope());
        assertFalse(SupportConditionConverter.convert(with(m, "JA0101", "Y"), "개인").targetScope() == TargetScope.CORP);
    }

    @Test
    @DisplayName("Y 판단: 공백·소문자 허용, N·빈 값·없는 코드는 Y 아님")
    void isY() {
        assertTrue(SupportConditionConverter.isY(" y "));
        assertFalse(SupportConditionConverter.isY("N"));
        assertFalse(SupportConditionConverter.isY(""));
        assertFalse(SupportConditionConverter.isY(null));
    }
}
