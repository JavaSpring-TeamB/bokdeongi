package com.bokdeongi.backend.sync;

import com.bokdeongi.backend.eligibility.label.LabelRuleCatalog;
import com.bokdeongi.backend.eligibility.service.EligibilityService;
import com.bokdeongi.backend.sync.client.ApiCallException;
import com.bokdeongi.backend.sync.client.CallBudget;
import com.bokdeongi.backend.sync.client.Gov24Client;
import com.bokdeongi.backend.sync.store.SyncStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 공공서비스 API 일일 증분 동기화 (DATA-01).
 *
 * <ol>
 *   <li>세 API(목록·상세·지원조건)를 전부 받는다. 하루 약 66회 호출(500건씩 22페이지 × 3)이라 한도 10,000에 여유가 있다.
 *       한 API라도 끝까지 못 받으면 DB를 바꾸지 않고 FAILED로 기록한다 (서비스는 마지막 성공 데이터로 동작)</li>
 *   <li>수정일시가 바뀐 혜택만 원문·가공값·코드 규칙을 다시 저장한다. 상태(EXPIRED)는 매번 다시 계산한다</li>
 *   <li>목록에서 사라진 혜택은 ENDED, 형식이 깨진 레코드는 격리(sync_error)</li>
 *   <li>판정에 영향이 있으면 회원 전체를 다시 판정한다 (ELIG-01)</li>
 * </ol>
 * 기록은 sync_logs 한 줄(operation = SERVICE_LIST, 세 API 호출 수 합계)로 남긴다.
 */
@Service
public class BenefitSyncService {

    private static final Logger log = LoggerFactory.getLogger(BenefitSyncService.class);

    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    static final String OPERATION = "SERVICE_LIST";
    /** 이보다 오래된 RUNNING은 멈춘 것으로 본다 */
    static final int RUNNING_TIMEOUT_HOURS = 3;

    /** 실행 결과 요약 */
    public record Report(String status, int apiCalls, int fetched, int created, int updated, int ended,
                         int quarantined, int rejudgedUsers, String message) {
    }

    private final Gov24Client client;
    private final SyncStore store;
    private final EligibilityService eligibility;
    private final int maxCalls;
    private final int dailyLimit;
    private final Clock clock = Clock.systemUTC();

    public BenefitSyncService(Gov24Client client, SyncStore store, EligibilityService eligibility,
            @Value("${app.sync.max-calls:300}") int maxCalls,
            @Value("${app.sync.daily-limit:10000}") int dailyLimit) {
        this.client = client;
        this.store = store;
        this.eligibility = eligibility;
        this.maxCalls = maxCalls;
        this.dailyLimit = dailyLimit;
    }

    public synchronized Report run() {
        // datetime(6)에 그대로 들어가게 마이크로초까지만 (sync_logs id를 시작 시각으로 다시 찾는다)
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC)).truncatedTo(ChronoUnit.MICROS);
        LocalDate today = LocalDate.now(clock.withZone(KOREA));

        store.closeStaleRunning(now.minusHours(RUNNING_TIMEOUT_HOURS), now);
        if (store.runningSince(now.minusHours(RUNNING_TIMEOUT_HOURS))) {
            log.info("다른 동기화가 진행 중이라 건너뜁니다");
            return skipped("다른 동기화가 진행 중");
        }
        LocalDateTime koreaMidnightUtc = today.atStartOfDay(KOREA).withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        int left = dailyLimit - store.callsSince(koreaMidnightUtc);
        CallBudget budget = new CallBudget(Math.min(maxCalls, left));
        if (budget.limit() <= 0) {
            log.warn("오늘 호출 한도를 다 써서 동기화를 건너뜁니다");
            return skipped("오늘 호출 한도 소진");
        }

        long logId = store.startLog(OPERATION, store.maxModifiedAt(), now);
        List<Map<String, Object>> list;
        List<Map<String, Object>> details;
        List<Map<String, Object>> conditions;
        try {
            list = client.fetchAll(Gov24Client.SERVICE_LIST, budget);
            details = client.fetchAll(Gov24Client.SERVICE_DETAIL, budget);
            conditions = client.fetchAll(Gov24Client.SUPPORT_CONDITIONS, budget);
        } catch (RuntimeException e) {
            // 호출 실패·한도 도달(ApiCallException)이나 예상 못 한 오류 모두 RUNNING으로 남기지 않고 닫는다
            String message = e instanceof ApiCallException ? e.getMessage()
                    : e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("동기화 실패(DB는 바꾸지 않음): {}", message);
            store.finishLog(logId, "FAILED", LocalDateTime.now(clock.withZone(ZoneOffset.UTC)), budget.used(), 0, 0, 0,
                    0, 0, message);
            return new Report("FAILED", budget.used(), 0, 0, 0, 0, 0, 0, message);
        }

        SyncPlan plan;
        try {
            SyncPlanner.Merged merged = SyncPlanner.merge(list, details, conditions);
            OrgRegionResolver regions = new OrgRegionResolver(merged.records(), store.loadRegions());
            BenefitMapper mapper = new BenefitMapper(regions, store.loadDocumentTypes(), store.loadHashtagNames(), today);
            plan = SyncPlanner.plan(merged, store.loadExisting(), mapper::map);
            store.apply(plan, detectChanges(plan), now);
        } catch (RuntimeException e) {
            log.error("동기화 반영 실패(이번 변경은 취소됨)", e);
            String message = e.getClass().getSimpleName() + ": " + e.getMessage();
            store.finishLog(logId, "FAILED", LocalDateTime.now(clock.withZone(ZoneOffset.UTC)), budget.used(),
                    list.size(), 0, 0, 0, 0, message);
            return new Report("FAILED", budget.used(), list.size(), 0, 0, 0, 0, 0, message);
        }

        int labelsApplied = applyLabels();
        String message = summary(plan);
        if (labelsApplied > 0) {
            message = (message.isEmpty() ? "" : message + " | ") + "검수 라벨 규칙 " + labelsApplied + "건 적용";
        }
        String status = plan.quarantined().isEmpty() && plan.endedSkipped() == null ? "SUCCESS" : "PARTIAL";
        store.finishLog(logId, status, LocalDateTime.now(clock.withZone(ZoneOffset.UTC)), budget.used(), plan.fetched(),
                plan.inserts().size(), plan.updates().size(), plan.endedIds().size(), plan.quarantined().size(),
                message.isEmpty() ? null : message);
        log.info("동기화 {}: 호출 {}회, 목록 {}건, 새 {}건, 갱신 {}건, 상태 변경 {}건, 종료 {}건, 격리 {}건", status, budget.used(),
                plan.fetched(), plan.inserts().size(), plan.updates().size(), plan.statusChanges().size(),
                plan.endedIds().size(), plan.quarantined().size());
        // 경고 내용은 sync_logs에 종류별 수만 남기고, 공고별 내용은 서버 로그로 (지역 미확인 기관 확인용)
        java.util.stream.Stream.concat(plan.inserts().stream(), plan.updates().stream().map(SyncPlan.Update::row))
                .forEach(r -> r.warnings().forEach(w -> log.info("가공 경고 {}: {}", r.serviceId(), w)));

        int rejudged = plan.affectsEligibility() || labelsApplied > 0 ? rejudgeAll() : 0;
        return new Report(status, budget.used(), plan.fetched(), plan.inserts().size(), plan.updates().size(),
                plan.endedIds().size(), plan.quarantined().size(), rejudged, message);
    }

    /** 원문이 바뀐 혜택의 변경 요약 (DATA-08). 종료됐다 다시 나온 혜택도 비교한다 */
    private Map<Long, ChangeDetector.Result> detectChanges(SyncPlan plan) {
        if (plan.updates().isEmpty()) {
            return Map.of();
        }
        Map<Long, ChangeDetector.Before> before = store.loadBefore(
                plan.updates().stream().map(SyncPlan.Update::id).toList());
        Map<Long, ChangeDetector.Result> out = new java.util.HashMap<>();
        for (SyncPlan.Update u : plan.updates()) {
            ChangeDetector.Before b = before.get(u.id());
            if (b != null) {
                out.put(u.id(), ChangeDetector.diff(b, u.row()));
            }
        }
        return out;
    }

    /**
     * 검수한 라벨(D-93)을 원문이 그대로인 혜택에 수동 규칙으로 넣는다. 실패해도 동기화 결과는 그대로 두고 경고만 남긴다.
     *
     * @return 이번에 새로 넣은 혜택 수
     */
    int applyLabels() {
        LabelRuleCatalog catalog = LabelRuleCatalog.standard();
        if (catalog.services().isEmpty()) {
            return 0;
        }
        try {
            LabelRulePlanner.Plan plan = LabelRulePlanner.plan(
                    store.loadLabelTargets(catalog.services().keySet()), catalog);
            store.applyLabels(plan.apply());
            if (!plan.stale().isEmpty()) {
                log.info("원문이 라벨 이후 바뀌어 라벨을 넣지 않은 혜택 {}건: {}", plan.stale().size(), plan.stale());
            }
            return plan.apply().size();
        } catch (RuntimeException e) {
            log.warn("검수 라벨 적용 실패(코드 규칙으로 계속): {}", e.getMessage());
            return 0;
        }
    }

    /** 혜택이 바뀌었으니 회원 전체를 다시 판정한다. 한 명이 실패해도 나머지는 계속 */
    private int rejudgeAll() {
        int done = 0;
        for (Long userId : store.loadUserIds()) {
            try {
                eligibility.judgeUser(userId);
                done++;
            } catch (RuntimeException e) {
                log.warn("회원 {} 재판정 실패: {}", userId, e.getMessage());
            }
        }
        log.info("동기화 후 회원 {}명 재판정", done);
        return done;
    }

    /** sync_logs.error_message에 남길 요약 (격리 사유 앞 5건, 종료 건너뜀, 경고 수) */
    static String summary(SyncPlan plan) {
        List<String> parts = new ArrayList<>();
        if (plan.endedSkipped() != null) {
            parts.add(plan.endedSkipped());
        }
        if (!plan.quarantined().isEmpty()) {
            List<String> samples = plan.quarantined().stream().limit(5)
                    .map(q -> (q.serviceId() == null ? "(ID 없음)" : q.serviceId()) + " " + q.reason()).toList();
            parts.add("격리 " + plan.quarantined().size() + "건: " + String.join(" / ", samples));
        }
        int warnings = plan.warningCount();
        if (warnings > 0) {
            List<String> types = new ArrayList<>();
            plan.warningsByType().forEach((type, n) -> types.add(type + " " + n));
            parts.add("가공 경고 " + warnings + "건 (" + String.join(", ", types.stream().limit(6).toList())
                    + (types.size() > 6 ? " 외" : "") + ")");
        }
        return String.join(" | ", parts);
    }

    private static Report skipped(String why) {
        return new Report("SKIPPED", 0, 0, 0, 0, 0, 0, 0, why);
    }
}
