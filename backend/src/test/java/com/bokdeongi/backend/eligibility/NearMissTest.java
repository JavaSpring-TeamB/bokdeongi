package com.bokdeongi.backend.eligibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 아깝게 탈락 (BEN-05, D-80): 시간이 지나면 충족되는 나이 하한만, 마감 전에 충족될 때만. */
class NearMissTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);
    private final EligibilityJudge judge = new EligibilityJudge();

    private static Rule rule(String field, Operator op, String... values) {
        return Rule.code(field, op, List.of(values), "공고 원문 근거");
    }

    private Optional<NearMiss> find(ProfileSnapshot p, List<Rule> rules, LocalDate applyEnd, boolean alwaysOpen) {
        EligibilityResult result = judge.judge(p, rules, TODAY);
        return NearMiss.find(p, rules, result, TODAY, applyEnd, alwaysOpen);
    }

    private static ProfileSnapshot born(int y, int m, int d) {
        return ProfileSnapshot.empty().withBirthDate(LocalDate.of(y, m, d));
    }

    @Test
    @DisplayName("나이 하한 미달 1개 + 상시 신청 → 그 나이가 되는 생일")
    void ageLowerBoundAlwaysOpen() {
        Optional<NearMiss> n = find(born(2009, 3, 2), List.of(rule("AGE", Operator.BETWEEN, "19", "34")), null, true);
        assertTrue(n.isPresent());
        assertEquals(LocalDate.of(2028, 3, 2), n.get().eligibleFrom());
        assertEquals("AGE", n.get().condition().field());
        assertFalse(n.get().othersNeedCheck());
    }

    @Test
    @DisplayName("충족되는 날이 마감 뒤면 제외, 마감 당일이면 포함")
    void deadline() {
        List<Rule> rules = List.of(rule("AGE", Operator.GTE, "19"));
        ProfileSnapshot p = born(2008, 12, 31);   // 2027-12-31에 만 19세
        assertTrue(find(p, rules, LocalDate.of(2027, 12, 30), false).isEmpty());
        assertTrue(find(p, rules, LocalDate.of(2027, 12, 31), false).isPresent());
        assertTrue(find(p, rules, null, false).isEmpty(), "마감을 모르면(공고 확인 필요·기관별 상이) 제외");
    }

    @Test
    @DisplayName("나이 상한 초과는 시간이 지나도 안 되므로 제외")
    void upperBoundIsNotNearMiss() {
        assertTrue(find(born(1991, 1, 1), List.of(rule("AGE", Operator.BETWEEN, "19", "34")), null, true).isEmpty());
        assertTrue(find(born(1991, 1, 1), List.of(rule("AGE", Operator.LTE, "34")), null, true).isEmpty());
    }

    @Test
    @DisplayName("바뀔 시점을 모르는 조건(성별·소득 등)이나 미충족 2개 이상은 제외")
    void otherConditionsAreNotNearMiss() {
        ProfileSnapshot p = born(2009, 3, 2).withGender("M");
        assertTrue(find(p, List.of(rule("GENDER", Operator.EQ, "F")), null, true).isEmpty());
        assertTrue(find(p, List.of(rule("GENDER", Operator.EQ, "F"), rule("AGE", Operator.GTE, "19")), null, true).isEmpty());
    }

    @Test
    @DisplayName("해당·조건부는 아깝게 탈락이 아님")
    void onlyIneligible() {
        assertTrue(find(born(2000, 1, 1), List.of(rule("AGE", Operator.GTE, "19")), null, true).isEmpty());
    }

    @Test
    @DisplayName("막내 자녀 나이 하한: 엔진이 연 나이로 비교하므로 그해 1월 1일")
    void youngestChildLowerBound() {
        ProfileSnapshot p = ProfileSnapshot.empty().withFamily("MARRIED", 1, 2025);
        Optional<NearMiss> n = find(p, List.of(rule("YOUNGEST_CHILD_AGE", Operator.GTE, "3")), null, true);
        assertEquals(LocalDate.of(2028, 1, 1), n.orElseThrow().eligibleFrom());
    }

    @Test
    @DisplayName("다른 조건이 확인 필요면 표시는 하되 othersNeedCheck = true")
    void othersUnknown() {
        ProfileSnapshot p = born(2009, 3, 2);   // 소득 미입력
        Optional<NearMiss> n = find(p, List.of(rule("AGE", Operator.GTE, "19"), rule("INCOME_PCT", Operator.LTE, "100")),
                null, true);
        assertTrue(n.orElseThrow().othersNeedCheck());
    }

    @Test
    @DisplayName("'또는' 묶음: 미충족이 1개뿐인 묶음의 나이 하한을 고른다 (D-62)")
    void alternativeGroups() {
        ProfileSnapshot p = born(1962, 6, 1).withHousing(null, "NONE");   // 2026-11 기준 64세, 수급자 아님
        List<Rule> rules = List.of(
                rule("AGE", Operator.GTE, "65").withGroup(1),
                rule("WELFARE", Operator.IN, "BASIC").withGroup(2));
        Optional<NearMiss> n = find(p, rules, null, true);
        assertEquals(LocalDate.of(2027, 6, 1), n.orElseThrow().eligibleFrom());
        assertEquals(1, n.get().condition().group());
    }

    @Test
    @DisplayName("공통 조건이 탈락하면 묶음의 나이 하한은 아깝게 탈락이 아님")
    void commonFailBlocksGroupAge() {
        ProfileSnapshot p = born(1962, 6, 1).withGender("M").withHousing(null, "BASIC");
        List<Rule> rules = List.of(
                rule("GENDER", Operator.EQ, "F"),
                rule("AGE", Operator.GTE, "65").withGroup(1),
                rule("WELFARE", Operator.IN, "BASIC").withGroup(2));
        assertTrue(find(p, rules, null, true).isEmpty(), "성별(바뀌지 않음) 하나만 탈락");
    }

    @Test
    @DisplayName("LLM 규칙의 소수 하한 등 정수가 아닌 값은 대상 아님")
    void nonIntegerBound() {
        Rule odd = new Rule(1L, "AGE", Operator.GTE, List.of("19.5"), "CODE", "근거", null);
        assertTrue(NearMiss.dateWhenMet(born(2009, 3, 2), odd, TODAY).isEmpty());
    }
}
