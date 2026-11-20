package com.bokdeongi.backend.benefit.assist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bokdeongi.backend.benefit.recommend.RecommendView.CheckType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EligibilityCheckViewTest {

    static final EligibilityCheckView.Source SOURCE = new EligibilityCheckView.Source("신혼부부 전세자금", "주거·자립",
            "무주택 신혼부부", "혼인 7년 이내");

    static Map<String, Object> t(int group, String field, String outcome, String reason, String quote) {
        Map<String, Object> m = new HashMap<>();
        m.put("group", group);
        m.put("field", field);
        m.put("outcome", outcome);
        m.put("reason", reason);
        m.put("expected", "기대값");
        m.put("actual", "회원값");
        m.put("sourceQuote", quote);
        return m;
    }

    @Test
    void 조건별_충족_확인필요_미충족과_근거를_묶음별로_보여준다() {
        List<Map<String, Object>> trace = List.of(
                t(0, "AGE", "PASS", null, "만 19~39세"),
                t(0, "ANSWER:married_within_7y", "UNKNOWN", "MISSING_VALUE", "혼인 7년 이내"),
                t(1, "HAS_HOUSE", "PASS", null, "무주택"),
                t(2, "INCOME_PCT", "FAIL", null, "중위소득 50% 이하"));
        EligibilityCheckResponse r = EligibilityCheckView.build(1L, "CONDITIONAL", null, trace, SOURCE, List.of());

        assertEquals("확인할 조건이 1개 있어요", r.summary());
        assertEquals(3, r.groups().size());
        assertEquals("공통 조건", r.groups().get(0).title());
        assertEquals("CHECK", r.groups().get(0).status());
        assertFalse(r.groups().get(0).alternative());
        assertEquals("다음 중 하나 (1)", r.groups().get(1).title());
        assertEquals("MET", r.groups().get(1).status());
        assertEquals("NOT_MET", r.groups().get(2).status());

        EligibilityCheckResponse.Condition age = r.groups().get(0).conditions().get(0);
        assertEquals("나이", age.label());
        assertEquals("MET", age.status());
        assertEquals("만 19~39세", age.sourceQuote());
        assertNull(age.checkType());

        EligibilityCheckResponse.Condition q = r.groups().get(0).conditions().get(1);
        assertEquals("추가 질문", q.label());
        assertEquals(CheckType.QUESTION, q.checkType());

        assertEquals(1, r.questions().size());
        assertEquals("married_within_7y", r.questions().get(0).key());
        assertTrue(r.next().canProceed());
        assertFalse(r.next().manualConfirmRequired());
        assertEquals("아래 질문에 답하면 바로 다시 확인해요", r.next().message());
        assertNull(r.next().nearMiss());
    }

    @Test
    void 같은_질문은_한_번만() {
        List<Map<String, Object>> trace = List.of(
                t(1, "ANSWER:public_rental", "UNKNOWN", "MISSING_VALUE", "공공임대"),
                t(2, "ANSWER:public_rental", "UNKNOWN", "MISSING_VALUE", "공공임대"));
        EligibilityCheckResponse r = EligibilityCheckView.build(1L, "CONDITIONAL", null, trace, SOURCE, List.of());
        assertEquals(1, r.questions().size());
    }

    @Test
    void 미충족이면_진행을_막고_아깝게_탈락과_비슷한_혜택을_보여준다() {
        List<Map<String, Object>> trace = List.of(t(0, "AGE", "FAIL", null, "만 40세 이상"),
                t(1, "ANSWER:married_within_7y", "UNKNOWN", "MISSING_VALUE", "혼인 7년 이내"));
        List<EligibilityCheckResponse.Similar> similar = List.of(new EligibilityCheckResponse.Similar(7L, "다른 혜택",
                "상시 신청"));
        EligibilityCheckResponse r = EligibilityCheckView.build(1L, "INELIGIBLE", "2030년 3월 2일부터 나이 조건을 충족해요",
                trace, SOURCE, similar);
        assertFalse(r.next().canProceed());
        assertEquals("충족하지 않는 조건이 있어요", r.summary());
        assertEquals("AGE", r.next().nearMiss().field());
        assertEquals("2030년 3월 2일부터 나이 조건을 충족해요", r.next().nearMiss().reason());
        assertEquals(1, r.next().similar().size());
        assertTrue(r.questions().isEmpty());   // 답해도 결과가 안 바뀐다
    }

    @Test
    void 프로필에_빠진_정보는_입력을_안내한다() {
        EligibilityCheckResponse r = EligibilityCheckView.build(1L, "CONDITIONAL", null,
                List.of(t(0, "HOUSEHOLD_TYPE", "UNKNOWN", "MISSING_VALUE", "한부모가족")), SOURCE, List.of());
        assertEquals("프로필에 빠진 정보를 입력하면 다시 확인해요", r.next().message());
        assertEquals(CheckType.PROFILE, r.groups().get(0).conditions().get(0).checkType());
    }

    @Test
    void 규칙으로_못_바꾼_조건은_원문을_보여주고_직접_확인을_받는다() {
        List<Map<String, Object>> trace = List.of(
                t(0, "AGE", "PASS", null, "만 19세 이상"),
                t(0, "UNPARSED", "UNKNOWN", "NEEDS_CHECK", "기타 시장이 인정하는 자"));
        EligibilityCheckResponse r = EligibilityCheckView.build(1L, "CONDITIONAL", null, trace, SOURCE, List.of());
        assertTrue(r.next().canProceed());
        assertTrue(r.next().manualConfirmRequired());
        assertEquals("[지원대상]\n무주택 신혼부부\n\n[선정기준]\n혼인 7년 이내", r.next().sourceText());
        assertEquals("공고 원문", r.groups().get(0).conditions().get(1).label());
        assertEquals(CheckType.SOURCE, r.groups().get(0).conditions().get(1).checkType());
    }

    @Test
    void 구조화_실패_공고도_직접_확인() {
        List<Map<String, Object>> trace = List.of(t(0, "RULES", "UNKNOWN", "UNSUPPORTED_RULE", "공고 조건을 구조화하지 못했습니다"));
        EligibilityCheckResponse r = EligibilityCheckView.build(1L, "CONDITIONAL", null, trace, SOURCE, List.of());
        assertTrue(r.next().manualConfirmRequired());
    }

    @Test
    void 경계값은_기관_문의를_권하고_진행은_허용() {
        List<Map<String, Object>> trace = List.of(t(0, "INCOME_PCT", "UNKNOWN", "BOUNDARY", "중위소득 75% 이하"));
        EligibilityCheckResponse r = EligibilityCheckView.build(1L, "CONDITIONAL", null, trace, SOURCE, List.of());
        assertTrue(r.next().canProceed());
        assertTrue(r.next().agencyCheckRecommended());
        assertFalse(r.next().manualConfirmRequired());
        assertTrue(r.questions().isEmpty());
        assertEquals(CheckType.AGENCY, r.groups().get(0).conditions().get(0).checkType());
    }

    @Test
    void 모두_충족이면_서류_준비로() {
        EligibilityCheckResponse r = EligibilityCheckView.build(1L, "ELIGIBLE", null,
                List.of(t(0, "AGE", "PASS", null, "만 19세 이상")), SOURCE, List.of());
        assertEquals("모든 조건을 충족해요", r.summary());
        assertEquals("다음 단계에서 낼 서류를 준비해요", r.next().message());
        assertEquals("신혼부부 전세자금", r.title());
    }
}
