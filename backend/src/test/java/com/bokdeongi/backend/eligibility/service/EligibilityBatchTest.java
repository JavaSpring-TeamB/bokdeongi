package com.bokdeongi.backend.eligibility.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bokdeongi.backend.eligibility.EligibilityJudge;
import com.bokdeongi.backend.eligibility.Rule;
import com.bokdeongi.backend.eligibility.store.BenefitTarget;
import com.bokdeongi.backend.eligibility.store.JsonText;
import com.bokdeongi.backend.eligibility.store.ProfileRow;
import com.bokdeongi.backend.eligibility.store.ResultRow;
import com.bokdeongi.backend.eligibility.store.RuleRow;
import com.bokdeongi.backend.eligibility.store.StoredResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 회원 한 명 × 혜택 여러 건 일괄 판정 (ELIG-01). DB 없이 저장될 줄을 확인한다. */
class EligibilityBatchTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 3, 0);
    private static final Map<String, String> REGIONS = Map.of("11", "서울특별시", "11620", "서울특별시 관악구",
            "26", "부산광역시");
    private final EligibilityBatch batch = new EligibilityBatch(new EligibilityJudge());

    /** 1999-05-01생, 서울 관악구, 소득 40%, 1인 가구, 구직자(JA0327), 무주택, 답 1개 */
    private static ProfileRow profile(String answersJson, String statusCodesJson) {
        return new ProfileRow(1L, LocalDate.of(1999, 5, 1), "F", "서울특별시", "관악구", new BigDecimal("40"), 1,
                "[\"JA0404\"]", statusCodesJson, "MONTHLY_RENT", false, "SINGLE", 0, null, "NONE", answersJson, 3);
    }

    private static BenefitTarget benefit(long id, String region, String scope) {
        return new BenefitTarget(id, region, scope, "PARSED", null, LocalDate.of(2026, 12, 31), "PARSED");
    }

    private static RuleRow rule(long id, long benefitId, int group, String field, String op, String value) {
        return new RuleRow(id, benefitId, group, field, op, value, "CODE", "근거 " + id, null);
    }

    private List<ResultRow> run(List<BenefitTarget> benefits, Map<Long, List<RuleRow>> rules,
            Map<Long, StoredResult> existing, ProfileRow p) {
        return batch.judgeAll(p, benefits, rules, REGIONS, existing, TODAY, NOW);
    }

    private static ResultRow of(List<ResultRow> rows, long benefitId) {
        return rows.stream().filter(r -> r.benefitId() == benefitId).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("해당·해당 없음·조건부와 기관 지역(D-56): 다른 시·도 공고는 해당 없음")
    void basicVerdicts() {
        List<ResultRow> rows = run(
                List.of(benefit(1, "11620", "PERSONAL"), benefit(2, "26", "PERSONAL"), benefit(3, null, "PERSONAL")),
                Map.of(1L, List.of(rule(10, 1, 0, "AGE", "BETWEEN", "[19, 34]")),
                        2L, List.of(rule(20, 2, 0, "AGE", "BETWEEN", "[19, 34]")),
                        3L, List.of(rule(30, 3, 0, "ANSWER:pre_training_done", "EQ", "[\"Y\"]"))),
                Map.of(), profile(null, "[\"JA0327\"]"));
        assertEquals("ELIGIBLE", of(rows, 1).result());
        assertEquals("INELIGIBLE", of(rows, 2).result());
        assertEquals("CONDITIONAL", of(rows, 3).result());
        assertTrue(of(rows, 1).traceJson().contains("\"field\":\"REGION\""), "기관 지역 규칙이 근거에 남는다");
        assertEquals(3, of(rows, 1).profileVersion());
    }

    @Test
    @DisplayName("시설·기업(CORP)은 빼고, 소상공인(SMALL_BIZ)은 자영업 회원만 조건부 (D-49, D-87)")
    void targetScope() {
        List<BenefitTarget> benefits = List.of(benefit(1, null, "CORP"), benefit(2, null, "SMALL_BIZ"));
        assertTrue(run(benefits, Map.of(), Map.of(), profile(null, "[\"JA0327\"]")).isEmpty());
        List<ResultRow> self = run(benefits, Map.of(), Map.of(), profile(null, "[\"SELF_EMPLOYED\"]"));
        assertEquals(1, self.size());
        assertEquals("CONDITIONAL", self.get(0).result());
    }

    @Test
    @DisplayName("규칙으로 못 바꾼 조건(D-64)은 그 묶음만 조건부, 규칙 구조화 실패(FAILED)는 최대 조건부")
    void unparsedAndFailed() {
        BenefitTarget partial = new BenefitTarget(1, null, "PERSONAL", "PARTIAL",
                "[{\"sourceQuote\": \"복지단체 등\", \"ruleGroup\": 2}]", null, "ALWAYS");
        BenefitTarget failed = new BenefitTarget(2, null, "PERSONAL", "FAILED", null, null, "ALWAYS");
        List<ResultRow> rows = run(List.of(partial, failed),
                Map.of(1L, List.of(rule(10, 1, 1, "AGE", "GTE", "[19]")),
                        2L, List.of(rule(20, 2, 0, "AGE", "GTE", "[19]"))),
                Map.of(), profile(null, null));
        assertEquals("ELIGIBLE", of(rows, 1).result(), "1번 묶음을 통과하면 2번 묶음의 원문 확인과 상관없이 해당");
        assertEquals("CONDITIONAL", of(rows, 2).result());
    }

    @Test
    @DisplayName("이전 결과 이어받기: previous_result, 처음 해당된 시각 유지, 새 줄/있던 줄 구분")
    void carryOver() {
        LocalDateTime firstTime = LocalDateTime.of(2026, 11, 1, 0, 0);
        List<ResultRow> rows = run(List.of(benefit(1, null, "PERSONAL"), benefit(2, null, "PERSONAL")),
                Map.of(1L, List.of(rule(10, 1, 0, "AGE", "GTE", "[19]")),
                        2L, List.of(rule(20, 2, 0, "AGE", "GTE", "[19]"))),
                Map.of(1L, new StoredResult(1, "ELIGIBLE", firstTime)), profile(null, null));
        assertEquals("ELIGIBLE", of(rows, 1).previousResult());
        assertEquals(firstTime, of(rows, 1).firstEligibleAt());
        assertTrue(of(rows, 1).exists());
        assertNull(of(rows, 2).previousResult());
        assertEquals(NOW, of(rows, 2).firstEligibleAt());
        assertFalse(of(rows, 2).exists());
    }

    @Test
    @DisplayName("아깝게 탈락: 나이 하한 하나만 미달이고 마감 전에 충족 (D-80)")
    void nearMiss() {
        ProfileRow young = new ProfileRow(1L, LocalDate.of(2008, 3, 2), "F", "서울특별시", "관악구", null, 1, "[]",
                "[]", null, null, null, null, null, null, null, 1);
        BenefitTarget open = new BenefitTarget(1, null, "PERSONAL", "PARSED", null, null, "ALWAYS");
        ResultRow r = run(List.of(open), Map.of(1L, List.of(rule(10, 1, 0, "AGE", "BETWEEN", "[19, 34]"))),
                Map.of(), young).get(0);
        assertEquals("INELIGIBLE", r.result());
        assertTrue(r.nearMiss());
        assertEquals("2027년 3월 2일부터 나이 조건을 충족해요", r.nearMissReason());
    }

    @Test
    @DisplayName("'아직 몰라요'(UNSURE)는 조건부, 모르는 질문은 missing_fields에 남는다 (D-90)")
    void answers() {
        List<ResultRow> rows = run(List.of(benefit(1, null, "PERSONAL"), benefit(2, null, "PERSONAL")),
                Map.of(1L, List.of(rule(10, 1, 0, "ANSWER:doctor", "EQ", "[\"Y\"]")),
                        2L, List.of(rule(20, 2, 0, "INCOME_PCT", "LTE", "[60]"),
                                rule(21, 2, 0, "HOUSEHOLD_SIZE", "LTE", "[2]"))),
                Map.of(), profile("{\"doctor\": \"UNSURE\"}", null));
        assertEquals("CONDITIONAL", of(rows, 1).result());
        assertTrue(of(rows, 1).traceJson().contains("\"reason\":\"USER_UNSURE\""));
        assertEquals("ELIGIBLE", of(rows, 2).result());
        assertEquals("[]", of(rows, 2).missingFieldsJson());
    }

    @Test
    @DisplayName("값·연산자를 못 읽는 규칙은 버리지 않고 조건부로 (해당으로 잘못 판정하지 않게)")
    void brokenRuleIsKept() {
        Rule r = EligibilityBatch.toRule(rule(10, 1, 0, "AGE", "BETWEEN_X", "[19, 34]"));
        assertEquals("INVALID:AGE", r.field());
        ResultRow row = run(List.of(benefit(1, null, "PERSONAL")),
                Map.of(1L, List.of(rule(10, 1, 0, "AGE", "GTE", "not json"))), Map.of(), profile(null, null)).get(0);
        assertEquals("CONDITIONAL", row.result());
    }

    @Test
    @DisplayName("건너뛴 칸(NULL)과 해당 없음([])을 구분해 엔진에 넘긴다 (D-45)")
    void snapshotKeepsNullVsEmpty() {
        var skipped = EligibilityBatch.toSnapshot(profile(null, null));
        var none = EligibilityBatch.toSnapshot(profile(null, "[]"));
        assertNull(skipped.statusCodes());
        assertTrue(none.statusCodes().isEmpty());
        assertEquals(List.of("JA0404"), JsonText.stringList("[\"JA0404\"]"));
    }
}
