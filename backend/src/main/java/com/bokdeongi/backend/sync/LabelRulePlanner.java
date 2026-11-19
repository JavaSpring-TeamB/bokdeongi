package com.bokdeongi.backend.sync;

import com.bokdeongi.backend.eligibility.label.LabelRuleCatalog;
import com.bokdeongi.backend.eligibility.label.LabelRuleCatalog.LabeledService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 검수한 라벨(D-93)을 어느 혜택에 넣을지 정한다. DB·Spring에 의존하지 않는다.
 *
 * <ul>
 *   <li>공고 원문의 수정일(한국 날짜)이 라벨을 만든 날과 같을 때만 넣는다. 다르면 라벨이 낡아 넣지 않고 코드 규칙만 둔다</li>
 *   <li>라벨이 있는 혜택은 원문 우선이라 코드 규칙을 지우고 라벨 규칙만 둔다 (DATA-02 원문 우선, D-65)</li>
 *   <li>이미 같은 라벨이 들어가 있으면 다시 쓰지 않는다 (매일 재판정이 돌지 않게)</li>
 * </ul>
 */
public final class LabelRulePlanner {

    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private LabelRulePlanner() {
    }

    /**
     * DB에 있는 라벨 대상 혜택의 지금 상태.
     *
     * @param totalRules    benefit_rules 줄 수 (출처 상관없이)
     * @param manualCurrent source = MANUAL이고 source_modified_at이 지금 원문 수정일시와 같은 줄 수
     */
    public record LabelTarget(long id, String serviceId, LocalDateTime apiModifiedAt, String rulesStatus,
                              String targetScope, int totalRules, int manualCurrent) {
    }

    public record LabelApply(LabelTarget target, LabeledService labels) {
    }

    /**
     * @param apply 넣을 것
     * @param stale 원문이 라벨 이후 바뀌어 넣지 않은 서비스ID
     */
    public record Plan(List<LabelApply> apply, List<String> stale) {
    }

    public static Plan plan(Collection<LabelTarget> targets, LabelRuleCatalog catalog) {
        List<LabelApply> apply = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (LabelTarget t : targets) {
            LabeledService labels = catalog.services().get(t.serviceId());
            if (labels == null || t.apiModifiedAt() == null) {
                continue;
            }
            if (!koreaDate(t.apiModifiedAt()).equals(labels.labeledOn())) {
                stale.add(t.serviceId());
                continue;
            }
            if (!alreadyApplied(t, labels)) {
                apply.add(new LabelApply(t, labels));
            }
        }
        return new Plan(apply, stale);
    }

    static boolean alreadyApplied(LabelTarget t, LabeledService labels) {
        int n = labels.rules().size();
        return t.totalRules() == n && t.manualCurrent() == n
                && Objects.equals(t.rulesStatus(), labels.rulesStatus())
                && (!labels.outOfScope() || "CORP".equals(t.targetScope()));
    }

    static java.time.LocalDate koreaDate(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA).toLocalDate();
    }
}
