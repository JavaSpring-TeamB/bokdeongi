package com.bokdeongi.backend.eligibility;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 기관 지역 규칙 붙이기 (D-56, D-88). */
class OrgRegionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);
    private static final String GWANAK = "서울특별시 관악구";
    private final EligibilityJudge judge = new EligibilityJudge();

    private static Rule rule(int group, String field, Operator op, String... values) {
        return new Rule(null, field, op, List.of(values), "MANUAL", "근거", null, group);
    }

    private static List<Integer> regionGroups(List<Rule> rules) {
        return rules.stream().filter(r -> OrgRegion.FIELD.equals(r.field())).map(Rule::group).sorted().toList();
    }

    @Test
    @DisplayName("원문에 지역이 없으면 공통 묶음(0)에 기관 지역")
    void defaultCommon() {
        List<Rule> out = OrgRegion.apply(List.of(rule(1, "AGE", Operator.LTE, "12"), rule(2, "AGE", Operator.GTE, "65")), GWANAK);
        assertEquals(List.of(0), regionGroups(out));
    }

    @Test
    @DisplayName("일부 묶음만 지역을 따로 정하면 나머지 묶음에만 기관 지역")
    void perGroup() {
        List<Rule> out = OrgRegion.apply(List.of(
                rule(1, "AGE", Operator.LTE, "12"),
                rule(2, "ANSWER:typhoid_high_risk", Operator.EQ, "Y"),
                rule(2, OrgRegion.FIELD, Operator.IN, "서울특별시")), GWANAK);
        assertEquals(List.of(1, 2), regionGroups(out));
        assertEquals(List.of(GWANAK), out.stream().filter(r -> r.group() == 1 && OrgRegion.FIELD.equals(r.field()))
                .findFirst().orElseThrow().values());
    }

    @Test
    @DisplayName("전국 기관(null)이거나 공통 지역을 원문이 정했으면 그대로")
    void unchanged() {
        List<Rule> rules = List.of(rule(0, OrgRegion.FIELD, Operator.IN, "부산광역시"), rule(0, "AGE", Operator.GTE, "19"));
        assertEquals(rules, OrgRegion.apply(rules, GWANAK));
        assertEquals(List.of(), regionGroups(OrgRegion.apply(List.of(rule(0, "AGE", Operator.GTE, "19")), null)));
    }

    @Test
    @DisplayName("관악구 예방접종: 동작구 주민은 장티푸스 고위험군이면 해당, 어린이 접종만 해당되면 해당 없음")
    void gwanakVaccination() {
        List<Rule> rules = OrgRegion.apply(List.of(
                rule(1, "AGE", Operator.LTE, "12"),
                rule(2, "ANSWER:typhoid_high_risk", Operator.EQ, "Y"),
                rule(2, OrgRegion.FIELD, Operator.IN, "서울특별시")), GWANAK);
        ProfileSnapshot dongjakChild = ProfileSnapshot.empty().withBirthDate(LocalDate.of(2020, 1, 1))
                .withRegion("서울특별시", "동작구").withAnswers(Map.of("typhoid_high_risk", "N"));
        ProfileSnapshot dongjakTraveler = ProfileSnapshot.empty().withBirthDate(LocalDate.of(1990, 1, 1))
                .withRegion("서울특별시", "동작구").withAnswers(Map.of("typhoid_high_risk", "Y"));
        ProfileSnapshot busanTraveler = dongjakTraveler.withRegion("부산광역시", "해운대구");
        ProfileSnapshot gwanakChild = dongjakChild.withRegion("서울특별시", "관악구");

        assertEquals(Verdict.INELIGIBLE, judge.judge(dongjakChild, rules, TODAY).verdict());
        assertEquals(Verdict.ELIGIBLE, judge.judge(dongjakTraveler, rules, TODAY).verdict());
        assertEquals(Verdict.INELIGIBLE, judge.judge(busanTraveler, rules, TODAY).verdict());
        assertEquals(Verdict.ELIGIBLE, judge.judge(gwanakChild, rules, TODAY).verdict());
    }
}
