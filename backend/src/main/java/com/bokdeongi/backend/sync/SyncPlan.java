package com.bokdeongi.backend.sync;

import java.util.List;

/**
 * 이번 동기화에서 DB에 할 일 (DATA-01). {@link SyncPlanner}가 만들고 SyncStore가 그대로 반영한다.
 *
 * @param inserts       새 혜택
 * @param updates       수정일시가 바뀌었거나, 종료됐다 다시 나왔거나, 지난번에 격리됐던 혜택 (원문·가공값 전체 갱신)
 * @param statusChanges 원문은 그대로인데 상태(ACTIVE↔EXPIRED)나 조회수만 바뀐 혜택
 * @param endedIds      API에서 사라진 혜택 (status = ENDED). 지우지 않는다
 * @param quarantined   형식이 깨져 이번에 반영하지 않은 레코드 (이전 데이터 유지, sync_error에 사유)
 * @param endedSkipped  받은 건수가 너무 적어 종료 처리를 건너뛰었으면 그 사유. 아니면 null
 * @param fetched       API 목록 건수
 */
public record SyncPlan(
        List<BenefitRow> inserts,
        List<Update> updates,
        List<StatusChange> statusChanges,
        List<Long> endedIds,
        List<Quarantine> quarantined,
        String endedSkipped,
        int fetched) {

    public record Update(long id, BenefitRow row) {
    }

    /** @param statusChanged 상태가 바뀌었는지 (false면 조회수만 바뀜) */
    public record StatusChange(long id, String status, Integer viewCount, boolean statusChanged) {
    }

    /** @param existingId DB에 이미 있으면 그 id (sync_error를 남긴다) */
    public record Quarantine(String serviceId, Long existingId, String reason) {
    }

    /** 판정 결과가 달라질 수 있는 변경이 있는지 (있으면 회원 전체를 다시 판정한다) */
    public boolean affectsEligibility() {
        return !inserts.isEmpty() || !updates.isEmpty() || !endedIds.isEmpty()
                || statusChanges.stream().anyMatch(StatusChange::statusChanged);
    }

    public int warningCount() {
        return inserts.stream().mapToInt(r -> r.warnings().size()).sum()
                + updates.stream().mapToInt(u -> u.row().warnings().size()).sum();
    }

    /** 가공 경고를 종류(':' 앞)별로 센다. 많은 순, 같으면 이름 순 */
    public java.util.LinkedHashMap<String, Integer> warningsByType() {
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        java.util.stream.Stream.concat(inserts.stream(), updates.stream().map(Update::row))
                .flatMap(r -> r.warnings().stream())
                .forEach(w -> counts.merge(w.contains(":") ? w.substring(0, w.indexOf(':')).strip() : w, 1, Integer::sum));
        java.util.LinkedHashMap<String, Integer> out = new java.util.LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(java.util.Map.Entry.comparingByKey()))
                .forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }
}
