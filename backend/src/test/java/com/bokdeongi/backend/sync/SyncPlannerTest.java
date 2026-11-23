package com.bokdeongi.backend.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SyncPlannerTest {

    private static final String T1 = "20261101090000";
    private static final LocalDateTime T1_UTC = LocalDateTime.of(2026, 11, 1, 0, 0);
    private static final String T2 = "20261117090000";

    private static final BenefitMapper MAPPER = new BenefitMapper(
            new OrgRegionResolver(List.of(), SyncFixtures.REGIONS), List.of(), Set.of(), LocalDate.of(2026, 11, 18));

    /** 세 API 응답을 만든다 */
    private static SyncPlanner.Merged api(String[]... services) {
        List<Map<String, Object>> list = new ArrayList<>();
        List<Map<String, Object>> detail = new ArrayList<>();
        List<Map<String, Object>> cond = new ArrayList<>();
        for (String[] s : services) {
            list.add(SyncFixtures.list(s[0], "혜택 " + s[0], "중앙행정기관", "보건복지부", "1352000", s[1]));
            detail.add(SyncFixtures.detail(s[0], "혜택 " + s[0], s[1]));
            cond.add(SyncFixtures.conditions(s[0]));
        }
        return SyncPlanner.merge(list, detail, cond);
    }

    private static ExistingBenefit db(long id, String serviceId, LocalDateTime modified, String status) {
        return new ExistingBenefit(id, serviceId, modified, status, 12, null);
    }

    @Test
    void 새_혜택은_INSERT_수정일시가_같으면_건드리지_않는다() {
        SyncPlan plan = SyncPlanner.plan(api(new String[] {"A", T1}, new String[] {"B", T1}),
                List.of(db(1, "A", T1_UTC, "ACTIVE")), MAPPER::map);
        assertEquals(1, plan.inserts().size());
        assertEquals("B", plan.inserts().get(0).serviceId());
        assertTrue(plan.updates().isEmpty());
        assertTrue(plan.statusChanges().isEmpty());
        assertTrue(plan.affectsEligibility());
    }

    @Test
    void 수정일시가_바뀌면_UPDATE() {
        SyncPlan plan = SyncPlanner.plan(api(new String[] {"A", T2}), List.of(db(1, "A", T1_UTC, "ACTIVE")),
                MAPPER::map);
        assertEquals(1, plan.updates().size());
        assertEquals(1L, plan.updates().get(0).id());
    }

    @Test
    void 종료됐다_다시_나오거나_격리됐던_혜택도_UPDATE() {
        SyncPlan plan = SyncPlanner.plan(api(new String[] {"A", T1}, new String[] {"B", T1}),
                List.of(db(1, "A", T1_UTC, "ENDED"), new ExistingBenefit(2, "B", T1_UTC, "ACTIVE", 12, "형식 오류")),
                MAPPER::map);
        assertEquals(2, plan.updates().size());
    }

    @Test
    void 원문이_같아도_기간이_지나면_상태만_바꾼다() {
        SyncPlan plan = SyncPlanner.plan(api(new String[] {"A", T1}), List.of(db(1, "A", T1_UTC, "EXPIRED")),
                MAPPER::map);
        assertEquals(1, plan.statusChanges().size());
        assertEquals("ACTIVE", plan.statusChanges().get(0).status());
        assertTrue(plan.statusChanges().get(0).statusChanged());
    }

    @Test
    void 조회수만_바뀌면_판정은_다시_하지_않는다() {
        SyncPlan plan = SyncPlanner.plan(api(new String[] {"A", T1}),
                List.of(new ExistingBenefit(1, "A", T1_UTC, "ACTIVE", 5, null)), MAPPER::map);
        assertEquals(1, plan.statusChanges().size());
        assertFalse(plan.statusChanges().get(0).statusChanged());
        assertFalse(plan.affectsEligibility());
    }

    @Test
    void 목록에서_사라지면_ENDED() {
        SyncPlan plan = SyncPlanner.plan(api(new String[] {"A", T1}),
                List.of(db(1, "A", T1_UTC, "ACTIVE"), db(2, "GONE", T1_UTC, "ACTIVE"), db(3, "OLD", T1_UTC, "ENDED")),
                MAPPER::map);
        assertEquals(List.of(2L), plan.endedIds());
        assertNull(plan.endedSkipped());
    }

    @Test
    void 받은_건수가_절반도_안_되면_종료_처리를_건너뛴다() {
        List<ExistingBenefit> existing = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            existing.add(db(i + 1, "S" + i, T1_UTC, "ACTIVE"));
        }
        SyncPlan plan = SyncPlanner.plan(api(new String[] {"S0", T1}), existing, MAPPER::map);
        assertTrue(plan.endedIds().isEmpty());
        assertNotNull(plan.endedSkipped());
    }

    @Test
    void 상세나_지원조건이_빠지면_격리하고_종료하지_않는다() {
        List<Map<String, Object>> list = List.of(
                SyncFixtures.list("A", "혜택 A", "중앙행정기관", "보건복지부", "1352000", T1),
                SyncFixtures.list("B", "혜택 B", "중앙행정기관", "보건복지부", "1352000", T1));
        SyncPlanner.Merged merged = SyncPlanner.merge(list, List.of(SyncFixtures.detail("A", "혜택 A", T1)),
                List.of(SyncFixtures.conditions("A"), SyncFixtures.conditions("B")));
        SyncPlan plan = SyncPlanner.plan(merged, List.of(db(2, "B", T1_UTC, "ACTIVE")), MAPPER::map);
        assertEquals(1, plan.quarantined().size());
        assertEquals(2L, plan.quarantined().get(0).existingId());
        assertTrue(plan.endedIds().isEmpty());
        assertEquals(1, plan.inserts().size());
    }

    @Test
    void 가공_중_오류는_그_레코드만_격리() {
        SyncPlan plan = SyncPlanner.plan(api(new String[] {"A", T1}, new String[] {"B", T1}), List.of(), rec -> {
            if (rec.serviceId().equals("B")) {
                throw new IllegalStateException("형식이 바뀜");
            }
            return MAPPER.map(rec);
        });
        assertEquals(1, plan.inserts().size());
        assertEquals(1, plan.quarantined().size());
        assertTrue(plan.quarantined().get(0).reason().contains("형식이 바뀜"));
    }

    @Test
    void 같은_서비스ID가_두_번_와도_한_번만() {
        SyncPlan plan = SyncPlanner.plan(api(new String[] {"A", T1}, new String[] {"A", T1}), List.of(), MAPPER::map);
        assertEquals(1, plan.inserts().size());
        assertEquals(1, plan.fetched());
    }

    @Test
    void 동기화_요약은_경고를_종류별로_센다() {
        // 시군구 기관인데 기관코드 표에 없으면 '지역 미확인' 경고
        Map<String, Object> l1 = SyncFixtures.list("A", "혜택 A", "시군구", "없는군청", "9999999", T1);
        Map<String, Object> l2 = SyncFixtures.list("B", "혜택 B", "시군구", "없는군청", "9999999", T1);
        Map<String, Object> d1 = SyncFixtures.detail("A", "혜택 A", T1);
        Map<String, Object> d2 = SyncFixtures.detail("B", "혜택 B", T1);
        d1.put("소관기관명", "없는군청");
        d2.put("소관기관명", "없는군청");
        SyncPlanner.Merged merged = SyncPlanner.merge(List.of(l1, l2), List.of(d1, d2),
                List.of(SyncFixtures.conditions("A"), SyncFixtures.conditions("B")));
        SyncPlan plan = SyncPlanner.plan(merged, List.of(), MAPPER::map);
        assertEquals(Integer.valueOf(2), plan.warningsByType().get("지역 미확인"));
        String summary = BenefitSyncService.summary(plan);
        assertTrue(summary.startsWith("가공 경고 " + plan.warningCount() + "건 ("), summary);
        assertTrue(summary.contains("지역 미확인 2"), summary);
    }
}
