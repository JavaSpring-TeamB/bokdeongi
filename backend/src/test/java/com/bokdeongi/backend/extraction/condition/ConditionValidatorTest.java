package com.bokdeongi.backend.extraction.condition;

import static com.bokdeongi.backend.extraction.condition.ConditionFixtures.answer;
import static com.bokdeongi.backend.extraction.condition.ConditionFixtures.c;
import static com.bokdeongi.backend.extraction.condition.ConditionFixtures.cheonan;
import static com.bokdeongi.backend.extraction.condition.ConditionFixtures.cheonanConditions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bokdeongi.backend.eligibility.Rule;
import com.bokdeongi.backend.eligibility.label.LabelRuleCatalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConditionValidatorTest {

    private static Rule rule(ConditionValidator.ServiceRules r, String field, int group) {
        return r.rules().stream().filter(x -> x.field().equals(field) && x.group() == group).findFirst().orElseThrow();
    }

    @Test
    void 두_번_같으면_모두_저장하고_신뢰도는_숫자_코드_반복으로_정한다() {
        ConditionValidator.ServiceRules r = ConditionValidator.validate(cheonan(), cheonanConditions(),
                cheonanConditions());
        assertEquals(6, r.rules().size());
        assertTrue(r.unparsed().isEmpty());
        assertEquals("DONE", r.rulesStatus());
        assertEquals("LLM", r.rules().get(0).source());
        // 나이: 숫자 O + 코드(14~64) 안 O + 반복 O
        assertEquals(new BigDecimal("1.00"), rule(r, "AGE", 1).confidence());
        // 장애: 숫자 없음(절반) + 코드 JA03 목록 안 O + 반복 O = 0.80 (D-61 기준선)
        assertEquals(new BigDecimal("0.80"), rule(r, "SENSITIVE", 3).confidence());
        // 질문·수급: 숫자·코드 둘 다 확인 못 함 → 0.65, 판정에서는 조건부
        assertEquals(new BigDecimal("0.65"), rule(r, "ANSWER:severe_disability", 3).confidence());
        assertEquals(new BigDecimal("0.65"), rule(r, "WELFARE", 2).confidence());
        assertEquals(ConditionValidator.LineStatus.LOW_CONFIDENCE, r.review().stream()
                .filter(l -> l.field().equals("WELFARE")).findFirst().orElseThrow().status());
    }

    @Test
    void 원문에_없는_근거는_버리고_같은_묶음을_조건부로_남긴다() {
        List<ConditionBatch.Condition> first = new ArrayList<>(cheonanConditions());
        first.add(c(5, "AGE", "GTE", List.of("65"), "65세 이상 어르신"));   // 다른 공고(관악) 문구
        List<ConditionBatch.Condition> second = new ArrayList<>(first);
        ConditionValidator.ServiceRules r = ConditionValidator.validate(cheonan(), first, second);
        assertFalse(r.rules().stream().anyMatch(x -> x.group() == 5));
        assertEquals(List.of(new LabelRuleCatalog.Unparsed(ConditionValidator.QUOTE_NOT_FOUND, 5)), r.unparsed());
        assertEquals("PARTIAL", r.rulesStatus());
    }

    @Test
    void 숫자가_근거와_다르면_버리고_근거_문구는_남긴다() {
        List<ConditionBatch.Condition> first = List.of(c(1, "AGE", "BETWEEN", List.of("60", "65"), "60~64세 주민"));
        ConditionValidator.ServiceRules r = ConditionValidator.validate(cheonan(), first, first);
        assertTrue(r.rules().isEmpty());
        assertEquals(List.of(new LabelRuleCatalog.Unparsed("60~64세 주민", 1)), r.unparsed());
        assertEquals(ConditionValidator.LineStatus.DROPPED, r.review().get(0).status());
    }

    @Test
    void 두_결과가_다르면_신뢰도가_낮고_두번째에만_있는_조건은_공고_전체를_조건부로() {
        List<ConditionBatch.Condition> second = new ArrayList<>(cheonanConditions());
        second.set(0, c(1, "AGE", "BETWEEN", List.of("60", "65"), "60~64세 주민"));
        ConditionValidator.ServiceRules r = ConditionValidator.validate(cheonan(), cheonanConditions(), second);
        assertEquals(new BigDecimal("0.70"), rule(r, "AGE", 1).confidence());
        assertTrue(r.unparsed().contains(new LabelRuleCatalog.Unparsed(ConditionValidator.REPEAT_MISMATCH, 0)));
        assertTrue(r.review().stream().anyMatch(l -> l.status() == ConditionValidator.LineStatus.REPEAT_ONLY));
    }

    @Test
    void 반복_비교는_IN_순서와_묶음_번호를_보지_않는다() {
        List<ConditionBatch.Condition> first = List.of(c(1, "SENSITIVE", "IN", List.of("JA0328", "JA0329"),
                "장애의 정도가 심한 장애인"));
        List<ConditionBatch.Condition> second = List.of(c(2, "SENSITIVE", "IN", List.of("JA0329", "JA0328"),
                "장애의 정도가 심한 장애인"));
        ConditionValidator.ServiceRules r = ConditionValidator.validate(cheonan(), first, second);
        assertEquals(new BigDecimal("0.80"), r.rules().get(0).confidence());
        assertTrue(r.unparsed().isEmpty());
    }

    @Test
    void 코드와_모순이면_신뢰도가_낮다() {
        List<ConditionBatch.Condition> first = List.of(c(4, "STATUS", "IN", List.of("JA0320"), "14세~59세 국가유공자"));
        ConditionValidator.ServiceRules r = ConditionValidator.validate(cheonan(), first, first);
        assertEquals(new BigDecimal("0.50"), r.rules().get(0).confidence());
    }

    @Test
    void 목록에_없는_필드는_버린다() {
        List<ConditionBatch.Condition> first = List.of(c(0, "JOB", "IN", List.of("farmer"), "60~64세 주민"));
        ConditionValidator.ServiceRules r = ConditionValidator.validate(cheonan(), first, first);
        assertTrue(r.rules().isEmpty());
        assertEquals(List.of(new LabelRuleCatalog.Unparsed("60~64세 주민", 0)), r.unparsed());
        assertEquals("목록에 없는 필드: JOB", r.review().get(0).reason());
    }

    @Test
    void 두_번_모두_시설_대상이면_CORP() {
        List<ConditionBatch.Condition> oos = List.of(c(0, "OUT_OF_SCOPE", null, List.of(), "60~64세 주민"));
        ConditionValidator.ServiceRules r = ConditionValidator.validate(cheonan(), oos, oos);
        assertTrue(r.outOfScope());
        assertTrue(r.rules().isEmpty());
        assertEquals("DONE", r.rulesStatus());
    }

    @Test
    void 한_번만_시설_대상이면_조건부() {
        List<ConditionBatch.Condition> oos = List.of(c(0, "OUT_OF_SCOPE", null, List.of(), "60~64세 주민"));
        ConditionValidator.ServiceRules r = ConditionValidator.validate(cheonan(), oos, cheonanConditions());
        assertFalse(r.outOfScope());
        assertTrue(r.unparsed().contains(new LabelRuleCatalog.Unparsed(ConditionValidator.REPEAT_MISMATCH, 0)));
    }

    @Test
    void 두_번_모두_조건_없음이면_규칙_없이_DONE() {
        List<ConditionBatch.Condition> none = List.of(c(0, "NONE", null, List.of(), "60~64세 주민"));
        ConditionValidator.ServiceRules r = ConditionValidator.validate(cheonan(), none, none);
        assertTrue(r.rules().isEmpty());
        assertEquals("DONE", r.rulesStatus());
    }

    @Test
    void LLM의_UNPARSED는_근거가_있으면_그대로() {
        List<ConditionBatch.Condition> first = List.of(c(2, "UNPARSED", null, List.of(), "장애의 정도가 심한 장애인"),
                c(3, "UNPARSED", null, List.of(), "저소득층"));
        ConditionValidator.ServiceRules r = ConditionValidator.validate(cheonan(), first, first);
        assertEquals(List.of(new LabelRuleCatalog.Unparsed("장애의 정도가 심한 장애인", 2),
                new LabelRuleCatalog.Unparsed(ConditionValidator.QUOTE_NOT_FOUND, 3)), r.unparsed());
    }

    @Test
    void 질문_문구는_검수_파일에_남는다() {
        List<ConditionBatch.Condition> first = List.of(answer(3, "severe_disability", "Y", "장애의 정도가 심한 장애인",
                "장애의 정도가 심한 장애인인가요?"));
        ConditionValidator.ServiceRules r = ConditionValidator.validate(cheonan(), first, first);
        assertEquals("장애의 정도가 심한 장애인인가요?", r.review().get(0).question());
        assertEquals("quote=O numbers=- code=- repeat=O", r.review().get(0).checks());
    }

    @Test
    void 질문_key_이름만_다르면_같은_조건으로_본다() {
        List<ConditionBatch.Condition> first = List.of(answer(3, "severe_disability", "Y", "장애의 정도가 심한 장애인",
                "장애의 정도가 심한 장애인인가요?"));
        List<ConditionBatch.Condition> second = List.of(answer(3, "disability_severe", "Y", "장애의 정도가 심한 장애인",
                "중증 장애인인가요?"));
        ConditionValidator.ServiceRules r = ConditionValidator.validate(cheonan(), first, second);
        assertEquals("ANSWER:severe_disability", r.rules().get(0).field());
        assertEquals(new BigDecimal("0.65"), r.rules().get(0).confidence());   // 반복 일치
        assertTrue(r.unparsed().isEmpty());
        assertTrue(r.review().stream().noneMatch(l -> l.status() == ConditionValidator.LineStatus.REPEAT_ONLY));
    }

    @Test
    void 질문_답이_다르거나_근거가_다르면_다른_조건() {
        List<ConditionBatch.Condition> first = List.of(answer(3, "severe_disability", "Y", "장애의 정도가 심한 장애인",
                "중증인가요?"));
        List<ConditionBatch.Condition> answerDiffers = List.of(answer(3, "x", "N", "장애의 정도가 심한 장애인", "?"));
        List<ConditionBatch.Condition> quoteDiffers = List.of(answer(3, "x", "Y", "14세~59세 국가유공자", "?"));
        assertTrue(ConditionValidator.validate(cheonan(), first, answerDiffers).unparsed()
                .contains(new LabelRuleCatalog.Unparsed(ConditionValidator.REPEAT_MISMATCH, 0)));
        assertTrue(ConditionValidator.validate(cheonan(), first, quoteDiffers).unparsed()
                .contains(new LabelRuleCatalog.Unparsed(ConditionValidator.REPEAT_MISMATCH, 0)));
    }

    @Test
    void 제외_대상_나이를_뒤집은_경계도_통과() {
        ConditionSource src = new ConditionSource(1L, "149200000026", "국민내일배움카드", "고용노동부",
                "만 15세 이상, 만 75세 이상자는 제외", null, null, null, cheonan().apiModifiedAt(), List.of(), false);
        List<ConditionBatch.Condition> first = List.of(c(0, "AGE", "LTE", List.of("74"), "만 75세 이상자는 제외"));
        ConditionValidator.ServiceRules r = ConditionValidator.validate(src, first, first);
        assertEquals(1, r.rules().size());
        assertTrue(r.unparsed().isEmpty());
    }
}
