package com.bokdeongi.backend.eligibility.label;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bokdeongi.backend.eligibility.EligibilityJudge;
import com.bokdeongi.backend.eligibility.OrgRegion;
import com.bokdeongi.backend.eligibility.ProfileSnapshot;
import com.bokdeongi.backend.eligibility.Rule;
import com.bokdeongi.backend.eligibility.UnknownReason;
import com.bokdeongi.backend.eligibility.Verdict;
import com.bokdeongi.backend.eligibility.label.LabelRuleCatalog.LabeledService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LabelRuleCatalogTest {

    private static final String CSV = """
            ﻿service_id,labeled_on,group,field,operator,values,source_quote,question
            A1,2026-05-08,0,AGE,BETWEEN,19;34,만 19~34세,
            A1,2026-05-08,0,ANSWER:public_rental,EQ,N,"공공임대주택, 거주",공공임대주택에 살고 있나요?
            A1,2026-05-08,2,UNPARSED,,,저소득층 등,
            B2,2026-08-07,0,NONE,,,지역 주민 모두,
            C3,2026-07-01,0,OUT_OF_SCOPE,,,양로시설 입소어르신,
            """;

    @Test
    void 줄을_규칙_원문확인_조건없음_시설대상으로_나눈다() {
        LabelRuleCatalog c = LabelRuleCatalog.parse(CSV);
        LabeledService a = c.services().get("A1");
        assertEquals(LocalDate.of(2026, 5, 8), a.labeledOn());
        assertEquals(2, a.rules().size());
        assertEquals("MANUAL", a.rules().get(0).source());
        assertEquals(List.of("19", "34"), a.rules().get(0).values());
        assertEquals("공공임대주택, 거주", a.rules().get(1).sourceQuote()); // 따옴표 안 쉼표
        assertEquals(List.of(new LabelRuleCatalog.Unparsed("저소득층 등", 2)), a.unparsed());
        assertEquals("PARTIAL", a.rulesStatus());

        LabeledService b = c.services().get("B2");
        assertTrue(b.rules().isEmpty());
        assertEquals("DONE", b.rulesStatus());
        assertFalse(b.outOfScope());
        assertTrue(c.services().get("C3").outOfScope());
    }

    @Test
    void 추가_질문_문구() {
        LabelRuleCatalog c = LabelRuleCatalog.parse(CSV);
        assertEquals("공공임대주택에 살고 있나요?", c.question("public_rental").orElseThrow());
        assertTrue(c.question("unknown_key").isEmpty());
    }

    @Test
    void 앱에_든_라벨_파일_30개_서비스를_엔진이_모두_읽는다() {
        LabelRuleCatalog c = LabelRuleCatalog.standard();
        assertEquals(30, c.services().size());
        EligibilityJudge judge = new EligibilityJudge();
        List<String> unsupported = new ArrayList<>();
        for (LabeledService s : c.services().values()) {
            List<Rule> rules = new ArrayList<>(s.rules());
            s.unparsed().forEach(u -> rules.add(Rule.unparsed(u.ruleGroup(), u.sourceQuote())));
            judge.judge(ProfileSnapshot.empty(), rules, LocalDate.of(2026, 11, 19)).trace().stream()
                    .filter(t -> t.reason() == UnknownReason.UNSUPPORTED_RULE)
                    .forEach(t -> unsupported.add(s.serviceId() + " " + t.field()));
        }
        assertTrue(unsupported.isEmpty(), "엔진이 못 읽는 라벨: " + unsupported);
        assertTrue(c.question("visually_impaired_household").isPresent());
    }

    @Test
    void 라벨로_끝까지_판정() {
        List<Rule> rules = OrgRegion.apply(LabelRuleCatalog.standard().services().get("643000000141").rules(), "충청북도");
        ProfileSnapshot base = ProfileSnapshot.empty().withRegion("충청북도", "청주시").withHousing(null, "BASIC");
        EligibilityJudge judge = new EligibilityJudge();
        LocalDate today = LocalDate.of(2026, 11, 19);
        assertEquals(Verdict.ELIGIBLE,
                judge.judge(base.withAnswers(Map.of("visually_impaired_household", "Y")), rules, today).verdict());
        assertEquals(Verdict.CONDITIONAL, judge.judge(base, rules, today).verdict());
    }
}
