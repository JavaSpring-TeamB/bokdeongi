package com.bokdeongi.backend.eligibility.store;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 판정 일괄 처리용 읽기·쓰기 (JDBC).
 *
 * <p>JPA 엔티티 대신 JDBC를 쓰는 이유: 회원 한 명당 혜택 약 1만 건을 한 번에 판정해 저장하므로,
 * 판정에 필요한 칸만 읽고(본문 text 칸 제외) 결과는 batchUpdate로 쓴다.
 * users는 A의 엔티티(auth.entity.User)를 건드리지 않으려고 칸을 직접 읽는다.
 */
@Repository
public class EligibilityStore {

    /** 목록에 나오는 공고만 판정한다. ENDED·EXPIRED는 목록에서 빠진다 (D-85) */
    static final String ACTIVE = "ACTIVE";

    private final JdbcTemplate jdbc;

    public EligibilityStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProfileRow> loadProfile(long userId) {
        List<ProfileRow> rows = jdbc.query("""
                SELECT id, birth_date, gender, sido_name, sigungu_name, median_income_pct, household_size,
                       household_type_codes, status_codes, housing_type, has_house, marital_status, children_count,
                       youngest_child_birth_year, welfare_status, answers, profile_version
                FROM users WHERE id = ?""", (rs, n) -> new ProfileRow(
                rs.getLong("id"), localDate(rs, "birth_date"), rs.getString("gender"), rs.getString("sido_name"),
                rs.getString("sigungu_name"), rs.getBigDecimal("median_income_pct"), integer(rs, "household_size"),
                rs.getString("household_type_codes"), rs.getString("status_codes"), rs.getString("housing_type"),
                bool(rs, "has_house"), rs.getString("marital_status"), integer(rs, "children_count"),
                integer(rs, "youngest_child_birth_year"), rs.getString("welfare_status"), rs.getString("answers"),
                rs.getInt("profile_version")), userId);
        return rows.stream().findFirst();
    }

    public List<BenefitTarget> loadActiveBenefits() {
        return jdbc.query("""
                SELECT id, region_code, target_scope, rules_status, unparsed_conditions, apply_end, date_status
                FROM benefits WHERE status = ?""", (rs, n) -> new BenefitTarget(
                rs.getLong("id"), rs.getString("region_code"), rs.getString("target_scope"),
                rs.getString("rules_status"), rs.getString("unparsed_conditions"), localDate(rs, "apply_end"),
                rs.getString("date_status")), ACTIVE);
    }

    /** ACTIVE 혜택의 규칙 전체를 benefit_id별로. */
    public Map<Long, List<RuleRow>> loadRulesOfActiveBenefits() {
        Map<Long, List<RuleRow>> byBenefit = new HashMap<>();
        jdbc.query("""
                SELECT r.id, r.benefit_id, r.rule_group, r.field, r.operator, r.value, r.source, r.source_quote,
                       r.confidence
                FROM benefit_rules r JOIN benefits b ON b.id = r.benefit_id
                WHERE b.status = ?
                ORDER BY r.benefit_id, r.rule_group, r.id""", (rs, n) -> {
            RuleRow row = new RuleRow(rs.getLong("id"), rs.getLong("benefit_id"), rs.getInt("rule_group"),
                    rs.getString("field"), rs.getString("operator"), rs.getString("value"), rs.getString("source"),
                    rs.getString("source_quote"), rs.getBigDecimal("confidence"));
            byBenefit.computeIfAbsent(row.benefitId(), k -> new ArrayList<>()).add(row);
            return null;
        }, ACTIVE);
        return byBenefit;
    }

    /** region_code → "서울특별시 관악구" (시군구 없는 시·도·세종은 시·도 이름만) */
    public Map<String, String> loadRegionNames() {
        Map<String, String> names = new LinkedHashMap<>();
        jdbc.query("SELECT region_code, sido_name, sigungu_name FROM regions", (rs, n) -> {
            String sigungu = rs.getString("sigungu_name");
            names.put(rs.getString("region_code"),
                    sigungu == null || sigungu.isBlank() ? rs.getString("sido_name")
                            : rs.getString("sido_name") + " " + sigungu);
            return null;
        });
        return names;
    }

    public Map<Long, StoredResult> loadResults(long userId) {
        Map<Long, StoredResult> out = new HashMap<>();
        jdbc.query("SELECT benefit_id, result, first_eligible_at FROM eligibility_results WHERE user_id = ?",
                (rs, n) -> {
                    StoredResult r = new StoredResult(rs.getLong("benefit_id"), rs.getString("result"),
                            localDateTime(rs, "first_eligible_at"));
                    out.put(r.benefitId(), r);
                    return null;
                }, userId);
        return out;
    }

    /** 새 줄은 INSERT, 있던 줄은 UPDATE (seen_at은 건드리지 않는다). */
    public void saveResults(long userId, List<ResultRow> rows) {
        List<Object[]> inserts = new ArrayList<>();
        List<Object[]> updates = new ArrayList<>();
        for (ResultRow r : rows) {
            Object[] common = {r.result(), r.previousResult(), r.nearMiss(), r.nearMissReason(), r.missingFieldsJson(),
                    r.traceJson(), r.profileVersion(), timestamp(r.firstEligibleAt()), timestamp(r.evaluatedAt())};
            if (r.exists()) {
                updates.add(append(common, userId, r.benefitId()));
            } else {
                inserts.add(append(new Object[] {userId, r.benefitId()}, common));
            }
        }
        if (!inserts.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO eligibility_results (user_id, benefit_id, result, previous_result, near_miss,
                        near_miss_reason, missing_fields, trace, profile_version, first_eligible_at, evaluated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", inserts);
        }
        if (!updates.isEmpty()) {
            jdbc.batchUpdate("""
                    UPDATE eligibility_results SET result = ?, previous_result = ?, near_miss = ?, near_miss_reason = ?,
                        missing_fields = ?, trace = ?, profile_version = ?, first_eligible_at = ?, evaluated_at = ?
                    WHERE user_id = ? AND benefit_id = ?""", updates);
        }
    }

    // ---------------------------------------------------------------- 변환

    private static Object[] append(Object[] head, Object... tail) {
        Object[] out = new Object[head.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(tail, 0, out, head.length, tail.length);
        return out;
    }

    private static java.time.LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toLocalDateTime();
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static Boolean bool(ResultSet rs, String column) throws SQLException {
        boolean v = rs.getBoolean(column);
        return rs.wasNull() ? null : v;
    }
}
