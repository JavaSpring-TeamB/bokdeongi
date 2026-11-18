package com.bokdeongi.backend.sync.store;

import com.bokdeongi.backend.benefit.service.DocumentPrepBuilder.MasterDoc;
import com.bokdeongi.backend.eligibility.Rule;
import com.bokdeongi.backend.eligibility.store.JsonText;
import com.bokdeongi.backend.sync.BenefitRow;
import com.bokdeongi.backend.sync.ExistingBenefit;
import com.bokdeongi.backend.sync.OrgRegionResolver.RegionRow;
import com.bokdeongi.backend.sync.SyncPlan;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동기화(DATA-01)의 DB 읽기·쓰기. 시각은 모두 UTC로 저장한다 (D-72).
 *
 * <p>benefits는 컬럼이 64개라 JPA 엔티티 대신 JDBC로 필요한 칸만 쓴다. 여러 줄 INSERT는 100줄씩 묶어 왕복 횟수를 줄인다.
 */
@Repository
public class SyncStore {

    static final int CHUNK = 100;
    static final String CODE = "CODE";

    /** API 원문·가공값 칸 (INSERT·UPDATE 공통, 순서가 {@link #values}와 같아야 한다) */
    static final List<String> COLUMNS = List.of(
            "service_id", "title", "purpose_text", "support_type", "target_text", "criteria_text", "content_text",
            "apply_method_text", "apply_period_text", "required_docs_text", "official_docs_text", "self_docs_text",
            "org_name", "dept_name", "receiving_org", "contact", "online_apply_url", "detail_url", "service_field",
            "user_type", "view_count", "api_modified_at", "status", "age_min", "age_max", "y_codes", "conditions_raw",
            "target_scope", "region_code", "apply_start", "apply_end", "date_status", "date_precision",
            "date_source_quote", "recurring_yearly", "max_amount", "amount_period", "annual_amount",
            "amount_confidence", "amount_source_quote", "exclusions", "documents", "online_apply", "self_doc_count",
            "hashtags", "hashtag_candidates");

    private final JdbcTemplate jdbc;

    public SyncStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ 읽기

    public List<ExistingBenefit> loadExisting() {
        return jdbc.query("SELECT id, service_id, api_modified_at, status, view_count, sync_error FROM benefits",
                (rs, n) -> new ExistingBenefit(rs.getLong("id"), rs.getString("service_id"),
                        localDateTime(rs, "api_modified_at"), rs.getString("status"), integer(rs, "view_count"),
                        rs.getString("sync_error")));
    }

    public List<RegionRow> loadRegions() {
        return jdbc.query("SELECT region_code, sido_name, sigungu_name FROM regions",
                (rs, n) -> new RegionRow(rs.getString("region_code"), rs.getString("sido_name"),
                        rs.getString("sigungu_name")));
    }

    public List<MasterDoc> loadDocumentTypes() {
        return jdbc.query("""
                SELECT id, name, aliases, issuer, online_available, issue_url, issue_method, default_validity_days,
                       alternatives, ocr_supported
                FROM document_types""", (rs, n) -> {
            List<String> aliases;
            try {
                aliases = JsonText.stringList(rs.getString("aliases"));
            } catch (IllegalArgumentException e) {
                aliases = null; // 별칭이 깨져 있어도 이름으로는 맞춘다
            }
            return new MasterDoc(rs.getLong("id"), rs.getString("name"), aliases == null ? List.of() : aliases,
                    rs.getString("issuer"), rs.getBoolean("online_available"), rs.getString("issue_url"),
                    rs.getString("issue_method"), integer(rs, "default_validity_days"), rs.getString("alternatives"),
                    rs.getBoolean("ocr_supported"));
        });
    }

    public Set<String> loadHashtagNames() {
        return new HashSet<>(jdbc.query("SELECT name FROM hashtags WHERE active = 1", (rs, n) -> rs.getString("name")));
    }

    public List<Long> loadUserIds() {
        return jdbc.query("SELECT id FROM users", (rs, n) -> rs.getLong("id"));
    }

    // ------------------------------------------------------------------ 동기화 기록 (sync_logs)

    /** since 이후(UTC) 시작한 동기화가 쓴 호출 수 합계. 오늘 한도 계산용 */
    public int callsSince(LocalDateTime sinceUtc) {
        List<Integer> sum = jdbc.query("SELECT COALESCE(SUM(api_calls), 0) AS calls FROM sync_logs WHERE started_at >= ?",
                (rs, n) -> rs.getInt("calls"), timestamp(sinceUtc));
        return sum.isEmpty() ? 0 : sum.get(0);
    }

    /** after 이후(UTC) 시작해 아직 RUNNING인 동기화가 있는지 (다른 서버·이전 실행과 겹치지 않게) */
    public boolean runningSince(LocalDateTime afterUtc) {
        return !jdbc.query("SELECT id FROM sync_logs WHERE status = 'RUNNING' AND started_at >= ?",
                (rs, n) -> rs.getLong("id"), timestamp(afterUtc)).isEmpty();
    }

    /** 오래된 RUNNING(서버가 중간에 꺼진 경우)은 FAILED로 닫는다 */
    public void closeStaleRunning(LocalDateTime beforeUtc, LocalDateTime nowUtc) {
        jdbc.update("""
                UPDATE sync_logs SET status = 'FAILED', finished_at = ?, error_message = '중간에 멈춤 (서버 재시작 등)'
                WHERE status = 'RUNNING' AND started_at < ?""", timestamp(nowUtc), timestamp(beforeUtc));
    }

    public LocalDateTime maxModifiedAt() {
        List<LocalDateTime> v = jdbc.query("SELECT MAX(api_modified_at) AS m FROM benefits",
                (rs, n) -> localDateTime(rs, "m"));
        return v.isEmpty() ? null : v.get(0);
    }

    /** RUNNING 기록을 남기고 그 id를 돌려준다 */
    public long startLog(String operation, LocalDateTime sinceModifiedAt, LocalDateTime startedAt) {
        jdbc.update("""
                INSERT INTO sync_logs (operation, since_modified_at, started_at, status, api_calls)
                VALUES (?, ?, ?, 'RUNNING', 0)""", operation, timestamp(sinceModifiedAt), timestamp(startedAt));
        List<Long> ids = jdbc.query("""
                SELECT id FROM sync_logs WHERE operation = ? AND started_at = ? AND status = 'RUNNING'
                ORDER BY id DESC LIMIT 1""", (rs, n) -> rs.getLong("id"), operation, timestamp(startedAt));
        return ids.isEmpty() ? -1 : ids.get(0);
    }

    public void finishLog(long id, String status, LocalDateTime finishedAt, int apiCalls, int fetched, int created,
            int updated, int ended, int quarantined, String errorMessage) {
        jdbc.update("""
                UPDATE sync_logs SET status = ?, finished_at = ?, api_calls = ?, fetched_count = ?, new_count = ?,
                    updated_count = ?, ended_count = ?, quarantined_count = ?, error_message = ?
                WHERE id = ?""", status, timestamp(finishedAt), apiCalls, fetched, created, updated, ended, quarantined,
                errorMessage, id);
    }

    // ------------------------------------------------------------------ 반영

    /**
     * 계획을 한 트랜잭션으로 반영한다. 중간에 실패하면 이번 변경은 모두 취소되고 마지막 성공 데이터가 남는다.
     */
    @Transactional
    public void apply(SyncPlan plan, LocalDateTime now) {
        Timestamp syncedAt = timestamp(now);

        // 1. 새 혜택: 여러 줄 INSERT → id 찾기 → 코드 규칙
        for (List<BenefitRow> chunk : chunks(plan.inserts())) {
            StringBuilder sql = new StringBuilder("INSERT INTO benefits (")
                    .append(String.join(", ", COLUMNS))
                    .append(", synced_at, rules_status, ai_status) VALUES ");
            String one = "(" + String.join(", ", Collections.nCopies(COLUMNS.size() + 3, "?")) + ")";
            List<Object> args = new ArrayList<>();
            for (int i = 0; i < chunk.size(); i++) {
                sql.append(i == 0 ? "" : ", ").append(one);
                args.addAll(values(chunk.get(i)));
                args.add(syncedAt);
                args.add("PENDING");
                args.add("PENDING");
            }
            jdbc.update(sql.toString(), args.toArray());
        }
        Map<String, Long> newIds = idsOf(plan.inserts().stream().map(BenefitRow::serviceId).toList());
        List<Object[]> ruleArgs = new ArrayList<>();
        for (BenefitRow row : plan.inserts()) {
            Long id = newIds.get(row.serviceId());
            if (id != null) {
                addRuleArgs(ruleArgs, id, row);
            }
        }

        // 2. 바뀐 혜택: 전체 갱신 → 코드 규칙 교체 → 원문이 바뀌어 낡은 LLM·수동 규칙 삭제 → 규칙 상태
        if (!plan.updates().isEmpty()) {
            String set = String.join(" = ?, ", COLUMNS) + " = ?";
            List<Object[]> updateArgs = new ArrayList<>();
            List<Object[]> idArgs = new ArrayList<>();
            List<Object[]> staleArgs = new ArrayList<>();
            for (SyncPlan.Update u : plan.updates()) {
                List<Object> args = new ArrayList<>(values(u.row()));
                args.add(syncedAt);
                args.add(u.id());
                updateArgs.add(args.toArray());
                idArgs.add(new Object[] {u.id()});
                staleArgs.add(new Object[] {u.id(), timestamp(u.row().apiModifiedAt())});
                addRuleArgs(ruleArgs, u.id(), u.row());
            }
            jdbc.batchUpdate("UPDATE benefits SET " + set
                    + ", synced_at = ?, sync_error = NULL, ai_status = 'PENDING' WHERE id = ?", updateArgs);
            jdbc.batchUpdate("DELETE FROM benefit_rules WHERE benefit_id = ? AND source = 'CODE'", idArgs);
            jdbc.batchUpdate("""
                    DELETE FROM benefit_rules WHERE benefit_id = ? AND source <> 'CODE'
                      AND (source_modified_at IS NULL OR source_modified_at <> ?)""", staleArgs);
        }
        insertRules(ruleArgs);
        if (!plan.updates().isEmpty()) {
            jdbc.batchUpdate("""
                    UPDATE benefits SET rules_status = 'PENDING', unparsed_conditions = NULL
                    WHERE id = ? AND NOT EXISTS (
                        SELECT 1 FROM benefit_rules r WHERE r.benefit_id = ? AND r.source <> 'CODE')""",
                    plan.updates().stream().map(u -> new Object[] {u.id(), u.id()}).toList());
        }

        // 3. 상태·조회수만 바뀐 혜택
        if (!plan.statusChanges().isEmpty()) {
            jdbc.batchUpdate("UPDATE benefits SET status = ?, view_count = ?, synced_at = ? WHERE id = ?",
                    plan.statusChanges().stream()
                            .map(c -> new Object[] {c.status(), c.viewCount(), syncedAt, c.id()}).toList());
        }

        // 4. API에서 사라진 혜택
        if (!plan.endedIds().isEmpty()) {
            jdbc.batchUpdate("UPDATE benefits SET status = 'ENDED', synced_at = ? WHERE id = ?",
                    plan.endedIds().stream().map(id -> new Object[] {syncedAt, id}).toList());
        }

        // 5. 격리: 데이터는 그대로 두고 사유만
        List<Object[]> quarantineArgs = plan.quarantined().stream().filter(q -> q.existingId() != null)
                .map(q -> new Object[] {cutError(q.reason()), syncedAt, q.existingId()}).toList();
        if (!quarantineArgs.isEmpty()) {
            jdbc.batchUpdate("UPDATE benefits SET sync_error = ?, synced_at = ? WHERE id = ?", quarantineArgs);
        }
    }

    private void addRuleArgs(List<Object[]> out, long benefitId, BenefitRow row) {
        for (Rule r : row.codeRules()) {
            out.add(new Object[] {benefitId, r.group(), r.field(), r.operator().name(), JsonText.writeStrings(r.values()),
                    CODE, r.sourceQuote(), timestamp(row.apiModifiedAt())});
        }
    }

    private void insertRules(List<Object[]> rows) {
        for (List<Object[]> chunk : chunks(rows)) {
            StringBuilder sql = new StringBuilder("""
                    INSERT INTO benefit_rules (benefit_id, rule_group, field, operator, `value`, source, source_quote,
                        source_modified_at) VALUES """);
            List<Object> args = new ArrayList<>();
            for (int i = 0; i < chunk.size(); i++) {
                sql.append(i == 0 ? "" : ", ").append("(?, ?, ?, ?, ?, ?, ?, ?)");
                Collections.addAll(args, chunk.get(i));
            }
            jdbc.update(sql.toString(), args.toArray());
        }
    }

    private Map<String, Long> idsOf(List<String> serviceIds) {
        Map<String, Long> out = new HashMap<>();
        for (List<String> chunk : chunks(serviceIds)) {
            String in = String.join(", ", Collections.nCopies(chunk.size(), "?"));
            jdbc.query("SELECT id, service_id FROM benefits WHERE service_id IN (" + in + ")", (rs, n) -> {
                out.put(rs.getString("service_id"), rs.getLong("id"));
                return null;
            }, chunk.toArray());
        }
        return out;
    }

    /** {@link #COLUMNS} 순서의 값 */
    static List<Object> values(BenefitRow r) {
        List<Object> v = new ArrayList<>(COLUMNS.size());
        Collections.addAll(v, r.serviceId(), r.title(), r.purposeText(), r.supportType(), r.targetText(),
                r.criteriaText(), r.contentText(), r.applyMethodText(), r.applyPeriodText(), r.requiredDocsText(),
                r.officialDocsText(), r.selfDocsText(), r.orgName(), r.deptName(), r.receivingOrg(), r.contact(),
                r.onlineApplyUrl(), r.detailUrl(), r.serviceField(), r.userType(), r.viewCount(),
                timestamp(r.apiModifiedAt()), r.status(), r.ageMin(), r.ageMax(), r.yCodesJson(), r.conditionsRawJson(),
                r.targetScope(), r.regionCode(), date(r.applyStart()), date(r.applyEnd()), r.dateStatus(),
                r.datePrecision(), r.dateSourceQuote(), r.recurringYearly(), r.maxAmount(), r.amountPeriod(),
                r.annualAmount(), r.amountConfidence(), r.amountSourceQuote(), r.exclusionsJson(), r.documentsJson(),
                r.onlineApply(), r.selfDocCount(), r.hashtagsJson(), r.hashtagCandidatesJson());
        return v;
    }

    private static String cutError(String s) {
        return s == null ? null : s.length() > 2000 ? s.substring(0, 2000) : s;
    }

    static <T> List<List<T>> chunks(List<T> items) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i += CHUNK) {
            out.add(items.subList(i, Math.min(items.size(), i + CHUNK)));
        }
        return out;
    }

    private static Timestamp timestamp(LocalDateTime v) {
        return v == null ? null : Timestamp.valueOf(v);
    }

    private static Date date(LocalDate v) {
        return v == null ? null : Date.valueOf(v);
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toLocalDateTime();
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }
}
