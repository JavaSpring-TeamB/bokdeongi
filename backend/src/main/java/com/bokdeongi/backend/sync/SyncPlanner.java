package com.bokdeongi.backend.sync;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * API 원문과 DB 상태를 비교해 할 일({@link SyncPlan})을 정한다 (DATA-01). DB·Spring에 의존하지 않는다.
 *
 * <ul>
 *   <li>수정일시가 같으면 원문 가공을 다시 저장하지 않는다(증분). 단 상태(EXPIRED)는 날짜가 지나며 바뀌므로 매번 다시 계산한다 (D-85)</li>
 *   <li>목록에 없는 혜택은 ENDED. 받은 건수가 기존 혜택의 절반도 안 되면 API 이상으로 보고 종료 처리를 건너뛴다</li>
 *   <li>상세·지원조건이 빠졌거나 형식이 깨진 레코드는 격리하고 기존 데이터를 그대로 둔다</li>
 * </ul>
 */
public final class SyncPlanner {

    static final String ENDED = "ENDED";
    /** 기존 혜택이 이 수보다 적으면(첫 적재·테스트 데이터) 건수 비교 없이 종료 처리한다 */
    static final int MIN_FOR_RATIO_CHECK = 100;

    private SyncPlanner() {
    }

    /** 세 API 응답을 서비스ID로 묶은 결과 */
    public record Merged(List<ServiceRecord> records, List<SyncPlan.Quarantine> quarantined, Set<String> listedIds) {
    }

    /**
     * 목록 기준으로 상세·지원조건을 붙인다. 목록에 있는데 상세나 지원조건이 없으면 격리(종료 아님).
     */
    public static Merged merge(List<Map<String, Object>> list, List<Map<String, Object>> details,
            List<Map<String, Object>> conditions) {
        Map<String, Map<String, Object>> detailById = byId(details);
        Map<String, Map<String, Object>> conditionById = byId(conditions);
        Map<String, ServiceRecord> records = new LinkedHashMap<>();
        List<SyncPlan.Quarantine> quarantined = new ArrayList<>();
        Set<String> listed = new HashSet<>();
        for (Map<String, Object> item : list) {
            String id = ServiceRecord.clean(item.get("서비스ID"));
            if (id == null) {
                quarantined.add(new SyncPlan.Quarantine(null, null, "목록에 서비스ID가 없는 레코드"));
                continue;
            }
            listed.add(id);
            if (records.containsKey(id)) {
                continue; // 페이지 경계에서 같은 건이 두 번 올 수 있다
            }
            Map<String, Object> detail = detailById.get(id);
            Map<String, Object> condition = conditionById.get(id);
            if (detail == null || condition == null) {
                quarantined.add(new SyncPlan.Quarantine(id, null,
                        (detail == null ? "상세" : "지원조건") + " 응답에 없음 (다음 동기화에서 다시 확인)"));
                continue;
            }
            records.put(id, new ServiceRecord(item, detail, condition));
        }
        return new Merged(new ArrayList<>(records.values()), quarantined, listed);
    }

    /**
     * @param merged   {@link #merge} 결과
     * @param existing DB에 있는 혜택 전체
     * @param mapper   원문 → benefits 한 줄
     */
    public static SyncPlan plan(Merged merged, Collection<ExistingBenefit> existing,
            Function<ServiceRecord, BenefitRow> mapper) {
        Map<String, ExistingBenefit> byServiceId = new HashMap<>();
        for (ExistingBenefit e : existing) {
            byServiceId.put(e.serviceId(), e);
        }
        List<BenefitRow> inserts = new ArrayList<>();
        List<SyncPlan.Update> updates = new ArrayList<>();
        List<SyncPlan.StatusChange> statusChanges = new ArrayList<>();
        List<SyncPlan.Quarantine> quarantined = new ArrayList<>();
        for (SyncPlan.Quarantine q : merged.quarantined()) {
            ExistingBenefit e = q.serviceId() == null ? null : byServiceId.get(q.serviceId());
            quarantined.add(new SyncPlan.Quarantine(q.serviceId(), e == null ? null : e.id(), q.reason()));
        }

        for (ServiceRecord rec : merged.records()) {
            ExistingBenefit old = byServiceId.get(rec.serviceId());
            BenefitRow row;
            try {
                row = mapper.apply(rec);
            } catch (RuntimeException ex) {
                // 형식이 바뀐 레코드 하나 때문에 전체가 멈추지 않게 격리한다
                quarantined.add(new SyncPlan.Quarantine(rec.serviceId(), old == null ? null : old.id(),
                        ex.getClass().getSimpleName() + ": " + ex.getMessage()));
                continue;
            }
            if (old == null) {
                inserts.add(row);
            } else if (!Objects.equals(old.apiModifiedAt(), row.apiModifiedAt()) || ENDED.equals(old.status())
                    || old.syncError() != null) {
                updates.add(new SyncPlan.Update(old.id(), row));
            } else {
                boolean statusChanged = !Objects.equals(old.status(), row.status());
                if (statusChanged || !Objects.equals(old.viewCount(), row.viewCount())) {
                    statusChanges.add(new SyncPlan.StatusChange(old.id(), row.status(), row.viewCount(), statusChanged));
                }
            }
        }

        List<Long> ended = new ArrayList<>();
        long alive = existing.stream().filter(e -> !ENDED.equals(e.status())).count();
        String endedSkipped = null;
        if (alive >= MIN_FOR_RATIO_CHECK && merged.listedIds().size() * 2 < alive) {
            endedSkipped = "목록 " + merged.listedIds().size() + "건이 기존 혜택 " + alive
                    + "건의 절반보다 적어 종료 처리를 건너뜀 (API 이상 의심)";
        } else {
            for (ExistingBenefit e : existing) {
                if (!ENDED.equals(e.status()) && !merged.listedIds().contains(e.serviceId())) {
                    ended.add(e.id());
                }
            }
        }
        return new SyncPlan(inserts, updates, statusChanges, ended, quarantined, endedSkipped, merged.listedIds().size());
    }

    private static Map<String, Map<String, Object>> byId(List<Map<String, Object>> items) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        for (Map<String, Object> item : items) {
            String id = ServiceRecord.clean(item.get("서비스ID"));
            if (id != null) {
                out.putIfAbsent(id, item);
            }
        }
        return out;
    }
}
