package com.bokdeongi.eligibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ELIG-01 규칙 엔진 단위 테스트. 경계값을 포함한다 (AGENTS.md 코드 작성 규칙). */
class EligibilityJudgeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    private final EligibilityJudge judge = new EligibilityJudge();

    private static Rule rule(String field, Operator op, String... values) {
        return Rule.code(field, op, List.of(values), "공고 원문 근거");
    }

    private static Rule llmRule(String field, Operator op, String confidence, String... values) {
        return new Rule(1L, field, op, List.of(values), "LLM", "공고 원문 근거", new BigDecimal(confidence));
    }

    private Outcome outcome(ProfileSnapshot p, Rule rule) {
        return judge.judge(p, List.of(rule), TODAY).trace().get(0).outcome();
    }

    private ConditionTrace trace(ProfileSnapshot p, Rule rule) {
        return judge.judge(p, List.of(rule), TODAY).trace().get(0);
    }

    // ---------- D-53 결합 규칙 ----------

    @Test
    @DisplayName("전부 통과면 해당, 규칙이 없어도 해당")
    void allPassIsEligible() {
        ProfileSnapshot p = ProfileSnapshot.empty().withGender("F").withBirthDate(LocalDate.of(2000, 1, 1));
        assertEquals(Verdict.ELIGIBLE, judge.judge(p, List.of(rule("GENDER", Operator.IN, "F"),
                rule("AGE", Operator.BETWEEN, "19", "34")), TODAY).verdict());
        assertEquals(Verdict.ELIGIBLE, judge.judge(p, List.of(), TODAY).verdict());
    }

    @Test
    @DisplayName("모름이 하나라도 있으면 조건부")
    void unknownMakesConditional() {
        ProfileSnapshot p = ProfileSnapshot.empty().withGender("F");
        EligibilityResult r = judge.judge(p, List.of(rule("GENDER", Operator.IN, "F"),
                rule("AGE", Operator.BETWEEN, "19", "34")), TODAY);
        assertEquals(Verdict.CONDITIONAL, r.verdict());
        assertEquals(List.of("AGE"), r.missingFields());
    }

    @Test
    @DisplayName("미충족이 하나라도 있으면 모름이 있어도 해당 없음")
    void failWinsOverUnknown() {
        ProfileSnapshot p = ProfileSnapshot.empty().withGender("M");
        EligibilityResult r = judge.judge(p, List.of(rule("GENDER", Operator.IN, "F"),
                rule("AGE", Operator.BETWEEN, "19", "34")), TODAY);
        assertEquals(Verdict.INELIGIBLE, r.verdict());
        assertEquals(1, r.failedCount());
    }

    @Test
    @DisplayName("공고 조건 구조화 실패면 다른 조건을 다 통과해도 조건부")
    void incompleteRulesAreConditional() {
        ProfileSnapshot p = ProfileSnapshot.empty().withGender("F");
        EligibilityResult r = judge.judge(p, List.of(rule("GENDER", Operator.IN, "F")), TODAY, true);
        assertEquals(Verdict.CONDITIONAL, r.verdict());
        assertEquals(UnknownReason.UNSUPPORTED_RULE, r.trace().get(1).reason());
    }

    @Test
    @DisplayName("같은 입력이면 항상 같은 결과")
    void deterministic() {
        ProfileSnapshot p = ProfileSnapshot.empty().withIncomePct(new BigDecimal("72"));
        List<Rule> rules = List.of(rule("INCOME_PCT", Operator.IN, "JA0201", "JA0202"));
        assertEquals(judge.judge(p, rules, TODAY), judge.judge(p, rules, TODAY));
    }

    // ---------- 나이 (만 나이, 경계 처리 없음) ----------

    @Test
    @DisplayName("만 19~34세: 생일 당일·전날 경계")
    void ageBoundaries() {
        Rule r = rule("AGE", Operator.BETWEEN, "19", "34");
        assertEquals(Outcome.PASS, outcome(ProfileSnapshot.empty().withBirthDate(LocalDate.of(2007, 11, 11)), r)); // 오늘 19세
        assertEquals(Outcome.FAIL, outcome(ProfileSnapshot.empty().withBirthDate(LocalDate.of(2007, 11, 12)), r)); // 내일 19세
        assertEquals(Outcome.PASS, outcome(ProfileSnapshot.empty().withBirthDate(LocalDate.of(1991, 11, 12)), r)); // 내일 35세
        assertEquals(Outcome.FAIL, outcome(ProfileSnapshot.empty().withBirthDate(LocalDate.of(1991, 11, 11)), r)); // 오늘 35세
    }

    @Test
    @DisplayName("생년월일을 건너뛰면 나이 조건은 모름(미입력)")
    void ageMissing() {
        ConditionTrace t = trace(ProfileSnapshot.empty(), rule("AGE", Operator.GTE, "65"));
        assertEquals(Outcome.UNKNOWN, t.outcome());
        assertEquals(UnknownReason.MISSING_VALUE, t.reason());
    }

    // ---------- 소득 (D-54 경계 ±5%p) ----------

    @Test
    @DisplayName("중위소득 75% 이하(JA0201·0202): 경계 ±5%p 안은 조건부")
    void incomeBracketBoundary() {
        Rule r = rule("INCOME_PCT", Operator.IN, "JA0201", "JA0202");
        assertEquals(Outcome.PASS, outcome(ProfileSnapshot.empty().withIncomePct(new BigDecimal("0")), r));
        assertEquals(Outcome.PASS, outcome(ProfileSnapshot.empty().withIncomePct(new BigDecimal("70")), r));
        ConditionTrace near = trace(ProfileSnapshot.empty().withIncomePct(new BigDecimal("70.5")), r);
        assertEquals(Outcome.UNKNOWN, near.outcome());
        assertEquals(UnknownReason.BOUNDARY, near.reason());
        assertTrue(near.boundary());
        assertEquals(Outcome.UNKNOWN, outcome(ProfileSnapshot.empty().withIncomePct(new BigDecimal("80")), r));
        assertEquals(Outcome.FAIL, outcome(ProfileSnapshot.empty().withIncomePct(new BigDecimal("80.1")), r));
    }

    @Test
    @DisplayName("LLM이 뽑은 '중위소득 60% 이하'도 같은 경계 규칙")
    void incomeNumberBoundary() {
        Rule r = llmRule("INCOME_PCT", Operator.LTE, "0.9", "60");
        assertEquals(Outcome.PASS, outcome(ProfileSnapshot.empty().withIncomePct(new BigDecimal("55")), r));
        assertEquals(Outcome.UNKNOWN, outcome(ProfileSnapshot.empty().withIncomePct(new BigDecimal("56")), r));
        assertEquals(Outcome.UNKNOWN, outcome(ProfileSnapshot.empty().withIncomePct(new BigDecimal("65")), r));
        assertEquals(Outcome.FAIL, outcome(ProfileSnapshot.empty().withIncomePct(new BigDecimal("65.1")), r));
    }

    @Test
    @DisplayName("소득을 모름으로 두면 조건부 + 추가 질문 대상")
    void incomeMissing() {
        EligibilityResult r = judge.judge(ProfileSnapshot.empty(), List.of(rule("INCOME_PCT", Operator.IN, "JA0201")), TODAY);
        assertEquals(Verdict.CONDITIONAL, r.verdict());
        assertEquals(List.of("INCOME_PCT"), r.missingFields());
    }

    // ---------- 지역 (D-56) ----------

    @Test
    @DisplayName("시·도 전체 / 시·군·구 / 일반구 / 통합특별시 옛 이름")
    void region() {
        ProfileSnapshot busan = ProfileSnapshot.empty().withRegion("부산광역시", "해운대구");
        assertEquals(Outcome.PASS, outcome(busan, rule("REGION", Operator.IN, "부산광역시")));
        assertEquals(Outcome.FAIL, outcome(busan, rule("REGION", Operator.IN, "부산광역시 부산진구")));
        assertEquals(Outcome.PASS, outcome(busan, rule("REGION", Operator.IN, "서울특별시", "부산광역시 해운대구")));
        ProfileSnapshot suwon = ProfileSnapshot.empty().withRegion("경기도", "수원시 장안구");
        assertEquals(Outcome.PASS, outcome(suwon, rule("REGION", Operator.IN, "경기도 수원시")));
        ProfileSnapshot gwangju = ProfileSnapshot.empty().withRegion("전남광주통합특별시", "북구");
        assertEquals(Outcome.PASS, outcome(gwangju, rule("REGION", Operator.IN, "광주광역시 북구")));
        assertEquals(Outcome.UNKNOWN, outcome(ProfileSnapshot.empty(), rule("REGION", Operator.IN, "부산광역시")));
    }

    // ---------- JA_GROUP (D-48) ----------

    @Test
    @DisplayName("JA03 일반 항목: 대학생·구직자 대상")
    void ja03General() {
        Rule r = rule("JA_GROUP:JA03", Operator.IN, "JA0320", "JA0327");
        assertEquals(Outcome.PASS, outcome(ProfileSnapshot.empty().withStatus(Set.of("JA0320"), null), r));
        assertEquals(Outcome.FAIL, outcome(ProfileSnapshot.empty().withStatus(Set.of(), Set.of()), r));
        assertEquals(UnknownReason.MISSING_VALUE, trace(ProfileSnapshot.empty(), r).reason());
    }

    @Test
    @DisplayName("JA03에 민감 항목이 섞이면: 일반으로 통과하거나, 민감을 모르면 조건부")
    void ja03Sensitive() {
        Rule r = rule("JA_GROUP:JA03", Operator.IN, "JA0302", "JA0326"); // 임산부, 근로자
        assertEquals(Outcome.PASS, outcome(ProfileSnapshot.empty().withStatus(Set.of("JA0326"), null), r));
        assertEquals(Outcome.PASS, outcome(ProfileSnapshot.empty().withStatus(Set.of(), Set.of("JA0302")), r));
        ConditionTrace t = trace(ProfileSnapshot.empty().withStatus(Set.of(), null), r);
        assertEquals(Outcome.UNKNOWN, t.outcome());
        assertEquals(UnknownReason.SENSITIVE_UNKNOWN, t.reason());
        assertEquals(Outcome.FAIL, outcome(ProfileSnapshot.empty().withStatus(Set.of(), Set.of()), r));
    }

    @Test
    @DisplayName("민감 항목 값은 근거에 '비공개'로만 남긴다")
    void sensitiveHidden() {
        Rule r = rule("JA_GROUP:JA03", Operator.IN, "JA0328");
        assertEquals("비공개", trace(ProfileSnapshot.empty().withStatus(Set.of(), Set.of("JA0328")), r).actual());
    }

    @Test
    @DisplayName("JA04 무주택(JA0412)은 has_house = false로 판정")
    void ja04NoHouse() {
        Rule r = rule("JA_GROUP:JA04", Operator.IN, "JA0404", "JA0412");
        assertEquals(Outcome.PASS, outcome(ProfileSnapshot.empty().withHousehold(1, Set.of(), false), r));
        assertEquals(Outcome.PASS, outcome(ProfileSnapshot.empty().withHousehold(1, Set.of("JA0404"), true), r));
        assertEquals(Outcome.FAIL, outcome(ProfileSnapshot.empty().withHousehold(2, Set.of(), true), r));
        assertEquals(Outcome.UNKNOWN, outcome(ProfileSnapshot.empty().withHousehold(2, Set.of(), null), r));
    }

    @Test
    @DisplayName("소상공인 조건(D-49): 자영업만 조건부, 나머지 해당 없음")
    void smallBusiness() {
        Rule r = rule("JA_GROUP:JA11", Operator.IN, "JA1102");
        ConditionTrace self = trace(ProfileSnapshot.empty().withStatus(Set.of(ProfileSnapshot.SELF_EMPLOYED), null), r);
        assertEquals(Outcome.UNKNOWN, self.outcome());
        assertEquals(UnknownReason.NEEDS_CHECK, self.reason());
        assertEquals(Outcome.FAIL, outcome(ProfileSnapshot.empty().withStatus(Set.of("JA0326"), null), r));
    }

    // ---------- 그 밖의 필드 ----------

    @Test
    @DisplayName("공고별 추가 질문 답 (PROF-06)")
    void answer() {
        Rule r = rule("ANSWER:no_house", Operator.EQ, "Y");
        assertEquals(UnknownReason.MISSING_VALUE, trace(ProfileSnapshot.empty(), r).reason());
        assertEquals(Outcome.PASS, outcome(ProfileSnapshot.empty().withAnswers(Map.of("no_house", "Y")), r));
        assertEquals(Outcome.FAIL, outcome(ProfileSnapshot.empty().withAnswers(Map.of("no_house", "N")), r));
    }

    @Test
    @DisplayName("막내 나이 6세 이하: 자녀 없음은 해당 없음, 출생연도 경계")
    void youngestChild() {
        Rule r = rule("YOUNGEST_CHILD_AGE", Operator.LTE, "6");
        assertEquals(Outcome.FAIL, outcome(ProfileSnapshot.empty().withFamily("MARRIED", 0, null), r));
        assertEquals(Outcome.PASS, outcome(ProfileSnapshot.empty().withFamily("MARRIED", 1, 2020), r));
        assertEquals(Outcome.FAIL, outcome(ProfileSnapshot.empty().withFamily("MARRIED", 1, 2019), r));
        assertEquals(Outcome.UNKNOWN, outcome(ProfileSnapshot.empty().withFamily("MARRIED", 1, null), r));
    }

    @Test
    @DisplayName("SENSITIVE 필드를 모르면 추가 질문이 아니라 동의 안내 대상")
    void sensitiveFieldNotInMissing() {
        EligibilityResult r = judge.judge(ProfileSnapshot.empty(), List.of(rule("SENSITIVE", Operator.IN, "JA0328")), TODAY);
        assertEquals(UnknownReason.SENSITIVE_UNKNOWN, r.trace().get(0).reason());
        assertTrue(r.missingFields().isEmpty());
    }

    @Test
    @DisplayName("LLM 규칙 신뢰도 0.8 미만은 조건부, 0.8은 정상 판정")
    void llmConfidence() {
        ProfileSnapshot p = ProfileSnapshot.empty().withHousing("MONTHLY", null);
        assertEquals(UnknownReason.LOW_CONFIDENCE, trace(p, llmRule("HOUSING", Operator.IN, "0.79", "MONTHLY")).reason());
        assertEquals(Outcome.PASS, outcome(p, llmRule("HOUSING", Operator.IN, "0.80", "MONTHLY")));
    }

    @Test
    @DisplayName("신뢰도가 비어 있는 LLM 규칙은 조건부, 코드 규칙은 신뢰도 없이 정상 판정")
    void llmWithoutConfidence() {
        ProfileSnapshot p = ProfileSnapshot.empty().withHousing("MONTHLY", null);
        Rule noScore = new Rule(1L, "HOUSING", Operator.IN, List.of("MONTHLY"), "LLM", "공고 원문 근거", null);
        assertEquals(UnknownReason.LOW_CONFIDENCE, trace(p, noScore).reason());
        assertEquals(Outcome.PASS, outcome(p, rule("HOUSING", Operator.IN, "MONTHLY")));
    }

    @Test
    @DisplayName("기준선은 생성자로 바꿀 수 있다 (정답셋 측정 후 조정)")
    void customThreshold() {
        ProfileSnapshot p = ProfileSnapshot.empty().withHousing("MONTHLY", null);
        Rule r = llmRule("HOUSING", Operator.IN, "0.65", "MONTHLY");
        assertEquals(Outcome.PASS, new EligibilityJudge(new BigDecimal("0.65")).evaluate(p, r, TODAY).outcome());
        assertEquals(UnknownReason.LOW_CONFIDENCE, trace(p, r).reason());
    }

    @Test
    @DisplayName("잘못된 규칙·모르는 필드는 예외 없이 조건부(원문 확인)")
    void badRulesDoNotThrow() {
        ProfileSnapshot p = ProfileSnapshot.empty().withBirthDate(LocalDate.of(2000, 1, 1));
        assertEquals(UnknownReason.UNSUPPORTED_RULE, trace(p, rule("AGE", Operator.BETWEEN, "19")).reason());
        assertEquals(UnknownReason.UNSUPPORTED_RULE, trace(p, rule("AGE", Operator.GTE, "열아홉")).reason());
        assertEquals(UnknownReason.UNSUPPORTED_RULE, trace(p, rule("SHOE_SIZE", Operator.EQ, "250")).reason());
        assertEquals(UnknownReason.UNSUPPORTED_RULE,
                trace(p.withIncomePct(BigDecimal.TEN), rule("INCOME_PCT", Operator.IN, "JA9999")).reason());
    }

    @Test
    @DisplayName("근거에 규칙 ID와 원문 문구가 남는다 (원칙 2)")
    void traceKeepsSource() {
        ConditionTrace t = trace(ProfileSnapshot.empty().withGender("F"), llmRule("GENDER", Operator.IN, "0.9", "F"));
        assertEquals(Long.valueOf(1L), t.ruleId());
        assertEquals("공고 원문 근거", t.sourceQuote());
        assertNull(t.reason());
        assertFalse(t.boundary());
    }

    // ---------- D-62 조건 묶음 · D-64 규칙으로 못 바꾼 조건 ----------

    /** 정답셋 449000000119: 60~64세 또는 (14~59세 그리고 수급자) 또는 (14~59세 그리고 보훈). */
    private static List<Rule> fluRules() {
        return List.of(
                rule("AGE", Operator.BETWEEN, "60", "64").withGroup(1),
                rule("AGE", Operator.BETWEEN, "14", "59").withGroup(2),
                rule("WELFARE", Operator.IN, "BASIC").withGroup(2),
                rule("AGE", Operator.BETWEEN, "14", "59").withGroup(3),
                rule("SENSITIVE", Operator.IN, "JA0329").withGroup(3));
    }

    private static ProfileSnapshot ageOn(int age) {
        return ProfileSnapshot.empty().withBirthDate(TODAY.minusYears(age));
    }

    @Test
    @DisplayName("묶음: 한 묶음이라도 전부 통과하면 해당")
    void anyGroupEligible() {
        assertEquals(Verdict.ELIGIBLE, judge.judge(ageOn(62), fluRules(), TODAY).verdict());
        ProfileSnapshot basic = ageOn(30).withHousing(null, "BASIC");
        assertEquals(Verdict.ELIGIBLE, judge.judge(basic, fluRules(), TODAY).verdict());
    }

    @Test
    @DisplayName("묶음: 모든 묶음이 탈락이면 해당 없음, 하나라도 미확인이면 조건부")
    void allGroupsFailOrUnknown() {
        ProfileSnapshot none = ageOn(30).withHousing(null, "NONE").withStatus(Set.of(), Set.of());
        assertEquals(Verdict.INELIGIBLE, judge.judge(none, fluRules(), TODAY).verdict());
        ProfileSnapshot skipped = ageOn(30).withHousing(null, "NONE");   // 민감정보 건너뜀
        assertEquals(Verdict.CONDITIONAL, judge.judge(skipped, fluRules(), TODAY).verdict());
        assertEquals(Verdict.INELIGIBLE, judge.judge(ageOn(70), fluRules(), TODAY).verdict());
    }

    @Test
    @DisplayName("묶음: 공통 조건(0번)이 탈락이면 다른 묶음이 통과해도 해당 없음")
    void commonGroupFails() {
        List<Rule> rules = List.of(rule("GENDER", Operator.IN, "F"),
                rule("AGE", Operator.BETWEEN, "12", "17").withGroup(1),
                rule("AGE", Operator.BETWEEN, "18", "26").withGroup(2));
        ProfileSnapshot male = ageOn(15).withGender("M");
        assertEquals(Verdict.INELIGIBLE, judge.judge(male, rules, TODAY).verdict());
        assertEquals(Verdict.ELIGIBLE, judge.judge(ageOn(15).withGender("F"), rules, TODAY).verdict());
    }

    @Test
    @DisplayName("묶음: 근거에 묶음 번호가 남는다")
    void traceKeepsGroup() {
        EligibilityResult r = judge.judge(ageOn(62), fluRules(), TODAY);
        assertEquals(List.of(1, 2, 2, 3, 3), r.trace().stream().map(ConditionTrace::group).toList());
    }

    @Test
    @DisplayName("아깝게 탈락: 묶음이 있으면 가장 덜 탈락한 묶음 기준")
    void failedCountWithGroups() {
        ProfileSnapshot p = ageOn(70).withHousing(null, "NONE").withStatus(Set.of(), Set.of());
        // 1번 묶음 1개 탈락(나이), 2번 2개, 3번 2개 → 1
        assertEquals(1, judge.judge(p, fluRules(), TODAY).failedCount());
    }

    @Test
    @DisplayName("규칙으로 못 바꾼 조건: 나머지가 모두 통과해도 조건부, 근거에 원문이 남는다")
    void unparsedMakesConditional() {
        List<Rule> rules = List.of(rule("AGE", Operator.BETWEEN, "19", "34"), Rule.unparsed(0, "저소득층 우선"));
        EligibilityResult r = judge.judge(ageOn(25), rules, TODAY);
        assertEquals(Verdict.CONDITIONAL, r.verdict());
        ConditionTrace t = r.trace().get(1);
        assertEquals(UnknownReason.NEEDS_CHECK, t.reason());
        assertEquals("저소득층 우선", t.sourceQuote());
        assertEquals(Verdict.INELIGIBLE, judge.judge(ageOn(40), rules, TODAY).verdict());
    }

    @Test
    @DisplayName("규칙으로 못 바꾼 조건이 '또는' 묶음이면, 다른 묶음이 탈락해도 해당 없음이 아니라 조건부")
    void unparsedAlternativeKeepsChance() {
        List<Rule> rules = List.of(rule("AGE", Operator.BETWEEN, "60", "64").withGroup(1),
                Rule.unparsed(2, "의사 판단하에 필요한 자"));
        assertEquals(Verdict.CONDITIONAL, judge.judge(ageOn(30), rules, TODAY).verdict());
        assertEquals(Verdict.ELIGIBLE, judge.judge(ageOn(62), rules, TODAY).verdict());
    }

    @Test
    @DisplayName("규칙으로 못 바꾼 조건은 신뢰도 기준선과 상관없이 원문 확인")
    void unparsedIgnoresConfidence() {
        ConditionTrace t = trace(ProfileSnapshot.empty(), Rule.unparsed(0, "모호한 기준"));
        assertEquals(UnknownReason.NEEDS_CHECK, t.reason());
    }

    @Test
    @DisplayName("묶음 번호는 0 이상")
    void negativeGroupRejected() {
        assertThrows(IllegalArgumentException.class, () -> rule("AGE", Operator.EQ, "1").withGroup(-1));
    }
}
