package com.bokdeongi.backend.benefit.assist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bokdeongi.backend.benefit.recommend.RecommendQuery;
import com.bokdeongi.backend.benefit.recommend.RecommendRow;
import com.bokdeongi.backend.benefit.recommend.RecommendSort;
import com.bokdeongi.backend.benefit.recommend.RecommendTab;
import com.bokdeongi.backend.eligibility.service.EligibilityService;
import com.bokdeongi.backend.eligibility.store.ResultRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class EligibilityCheckServiceTest {

    static class FakeEligibility extends EligibilityService {
        ResultRow row;
        int judged;

        FakeEligibility(ResultRow row) {
            super(null, null);
            this.row = row;
        }

        @Override
        public Optional<ResultRow> judgeUserBenefit(long userId, long benefitId) {
            judged++;
            return Optional.ofNullable(row);
        }
    }

    static class FakeStore extends EligibilityCheckStore {
        FakeStore() {
            super(null);
        }

        @Override
        public Optional<EligibilityCheckView.Source> source(long benefitId) {
            return Optional.of(EligibilityCheckViewTest.SOURCE);
        }
    }

    static class FakeQuery extends RecommendQuery {
        final List<String> calls = new ArrayList<>();

        FakeQuery() {
            super(null);
        }

        @Override
        public List<RecommendRow> page(long userId, RecommendTab tab, Filter filter, RecommendSort sort, int page,
                int size) {
            calls.add(tab + "/" + filter.field() + "/" + sort + "/" + size);
            List<RecommendRow> out = new ArrayList<>();
            for (long id : new long[] {1L, 2L, 3L, 4L}) {
                out.add(new RecommendRow(id, "혜택" + id, "주거·자립", null, null, null, null, null, null, null,
                        "ALWAYS", null, false, null, null, null, null, null, null, null, "ELIGIBLE", null, false,
                        null, null, null, false));
            }
            return out;
        }
    }

    private static ResultRow row(String result) {
        return new ResultRow(1L, result, null, false, null, "[]",
                "[{\"group\":0,\"field\":\"AGE\",\"outcome\":\"" + ("INELIGIBLE".equals(result) ? "FAIL" : "PASS")
                        + "\",\"sourceQuote\":\"만 19세 이상\"}]",
                1, null, null, true);
    }

    @Test
    void 들어올_때마다_그_혜택만_다시_판정한다() {
        FakeEligibility e = new FakeEligibility(row("ELIGIBLE"));
        FakeQuery q = new FakeQuery();
        EligibilityCheckResponse r = new EligibilityCheckService(e, new FakeStore(), q).check(10L, 1L).orElseThrow();
        assertEquals(1, e.judged);
        assertEquals("ELIGIBLE", r.result());
        assertTrue(q.calls.isEmpty());   // 해당이면 비슷한 혜택을 찾지 않는다
    }

    @Test
    void 미충족이면_같은_분야_해당_혜택을_자기를_빼고_3건() {
        FakeQuery q = new FakeQuery();
        EligibilityCheckResponse r = new EligibilityCheckService(new FakeEligibility(row("INELIGIBLE")),
                new FakeStore(), q).check(10L, 1L).orElseThrow();
        assertEquals(List.of("ELIGIBLE/주거·자립/DEADLINE/4"), q.calls);
        assertEquals(List.of(2L, 3L, 4L), r.next().similar().stream().map(EligibilityCheckResponse.Similar::benefitId)
                .toList());
        assertEquals("상시 신청", r.next().similar().get(0).period());
    }

    @Test
    void 종료됐거나_개인_대상이_아니면_비어_있다() {
        assertTrue(new EligibilityCheckService(new FakeEligibility(null), new FakeStore(), new FakeQuery())
                .check(10L, 1L).isEmpty());
    }
}
