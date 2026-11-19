package com.bokdeongi.backend.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bokdeongi.backend.eligibility.label.LabelRuleCatalog;
import com.bokdeongi.backend.sync.LabelRulePlanner.LabelTarget;
import com.bokdeongi.backend.sync.LabelRulePlanner.Plan;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LabelRulePlannerTest {

    private static final LabelRuleCatalog CATALOG = LabelRuleCatalog.parse("""
            service_id,labeled_on,group,field,operator,values,source_quote,question
            A1,2026-05-08,0,AGE,BETWEEN,19;34,만 19~34세,
            A1,2026-05-08,0,GENDER,EQ,F,여성,
            C3,2026-07-01,0,OUT_OF_SCOPE,,,양로시설 입소어르신,
            """);

    /** 2026-05-08 09:00 한국 시각 = UTC 00:00 */
    private static final LocalDateTime LABELED_UTC = LocalDateTime.of(2026, 5, 8, 0, 0);

    @Test
    void 원문_수정일이_라벨과_같으면_넣는다() {
        Plan p = LabelRulePlanner.plan(List.of(new LabelTarget(1, "A1", LABELED_UTC, "PENDING", "PERSONAL", 3, 0)),
                CATALOG);
        assertEquals(1, p.apply().size());
        assertTrue(p.stale().isEmpty());
    }

    @Test
    void 한국_날짜로_비교한다() {
        // UTC 5/7 16:00 = 한국 5/8 01:00 → 같은 날
        Plan p = LabelRulePlanner.plan(
                List.of(new LabelTarget(1, "A1", LocalDateTime.of(2026, 5, 7, 16, 0), "PENDING", "PERSONAL", 0, 0)),
                CATALOG);
        assertEquals(1, p.apply().size());
    }

    @Test
    void 원문이_바뀌었으면_넣지_않는다() {
        Plan p = LabelRulePlanner.plan(
                List.of(new LabelTarget(1, "A1", LocalDateTime.of(2026, 11, 1, 0, 0), "PENDING", "PERSONAL", 3, 0)),
                CATALOG);
        assertTrue(p.apply().isEmpty());
        assertEquals(List.of("A1"), p.stale());
    }

    @Test
    void 이미_들어가_있으면_다시_쓰지_않는다() {
        Plan p = LabelRulePlanner.plan(List.of(
                new LabelTarget(1, "A1", LABELED_UTC, "DONE", "PERSONAL", 2, 2),
                new LabelTarget(3, "C3", LocalDateTime.of(2026, 7, 1, 0, 0), "DONE", "CORP", 0, 0)), CATALOG);
        assertTrue(p.apply().isEmpty());
    }

    @Test
    void 코드_규칙이_섞여_있거나_시설대상_표시가_없으면_다시_넣는다() {
        Plan p = LabelRulePlanner.plan(List.of(
                new LabelTarget(1, "A1", LABELED_UTC, "DONE", "PERSONAL", 3, 2),
                new LabelTarget(3, "C3", LocalDateTime.of(2026, 7, 1, 0, 0), "DONE", "PERSONAL", 0, 0)), CATALOG);
        assertEquals(2, p.apply().size());
    }

    @Test
    void 라벨_없는_혜택은_건드리지_않는다() {
        Plan p = LabelRulePlanner.plan(List.of(new LabelTarget(9, "ZZ", LABELED_UTC, "PENDING", "PERSONAL", 3, 0)),
                CATALOG);
        assertTrue(p.apply().isEmpty());
        assertTrue(p.stale().isEmpty());
    }
}
