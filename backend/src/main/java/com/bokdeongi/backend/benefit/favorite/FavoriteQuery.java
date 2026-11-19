package com.bokdeongi.backend.benefit.favorite;

import com.bokdeongi.backend.benefit.recommend.RecommendRow;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 관심·신청 중 혜택 조회 (BEN-12, BEN-09). 판정 결과가 없는 혜택도 나오게 eligibility_results는 LEFT JOIN */
@Repository
public class FavoriteQuery {

    /** 경고·목록 대상: 관심 저장했거나 신청을 진행 중(결과 전)인 혜택 */
    static final String TRACKED = "(ub.bookmarked = TRUE OR ub.app_status IN ('IN_PROGRESS', 'SUBMITTED'))";

    private final JdbcTemplate jdbc;

    public FavoriteQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FavoriteRow> tracked(long userId) {
        return jdbc.query("""
                SELECT ub.id AS ub_id, ub.bookmarked, ub.bookmarked_at, ub.app_status,
                       b.id, b.title, b.service_field, b.support_type, COALESCE(b.summary_what, b.purpose_text) AS summary,
                       b.max_amount, b.amount_period, b.annual_amount, b.apply_start, b.apply_end, b.date_status,
                       b.date_precision, b.recurring_yearly, b.online_apply, b.self_doc_count, b.hashtags, b.detail_url,
                       b.online_apply_url, b.org_name, COALESCE(rg.sido_name, '전국') AS region_name, b.status,
                       b.exclusions, b.last_change_summary, b.last_changed_at,
                       e.result, e.previous_result, e.near_miss, e.near_miss_reason, e.missing_fields, e.trace
                FROM user_benefits ub
                JOIN benefits b ON b.id = ub.benefit_id
                LEFT JOIN regions rg ON rg.region_code = b.region_code
                LEFT JOIN eligibility_results e ON e.user_id = ub.user_id AND e.benefit_id = ub.benefit_id
                WHERE ub.user_id = ? AND """ + TRACKED, (rs, n) -> row(rs), userId);
    }

    /** 해시태그 이름 → 분류(분야·지원형태·대상) */
    public Map<String, String> tagCategories() {
        Map<String, String> out = new HashMap<>();
        jdbc.query("SELECT name, category FROM hashtags", (rs, n) -> {
            out.put(rs.getString("name"), rs.getString("category"));
            return null;
        });
        return out;
    }

    private static FavoriteRow row(ResultSet rs) throws SQLException {
        RecommendRow card = new RecommendRow(rs.getLong("id"), rs.getString("title"), rs.getString("service_field"),
                rs.getString("support_type"), rs.getString("summary"), longOrNull(rs, "max_amount"),
                rs.getString("amount_period"), longOrNull(rs, "annual_amount"), localDate(rs, "apply_start"),
                localDate(rs, "apply_end"), rs.getString("date_status"), rs.getString("date_precision"),
                rs.getBoolean("recurring_yearly"), boolOrNull(rs, "online_apply"), intOrNull(rs, "self_doc_count"),
                rs.getString("hashtags"), rs.getString("detail_url"), rs.getString("online_apply_url"),
                rs.getString("org_name"), rs.getString("region_name"), rs.getString("result"),
                rs.getString("previous_result"), rs.getBoolean("near_miss"), rs.getString("near_miss_reason"),
                rs.getString("missing_fields"), rs.getString("trace"), rs.getBoolean("bookmarked"));
        return new FavoriteRow(card, rs.getLong("ub_id"), rs.getBoolean("bookmarked"),
                localDateTime(rs, "bookmarked_at"), rs.getString("app_status"), rs.getString("status"),
                rs.getString("exclusions"), rs.getString("last_change_summary"), localDateTime(rs, "last_changed_at"));
    }

    private static LocalDate localDate(ResultSet rs, String c) throws SQLException {
        Date d = rs.getDate(c);
        return d == null ? null : d.toLocalDate();
    }

    private static LocalDateTime localDateTime(ResultSet rs, String c) throws SQLException {
        Timestamp t = rs.getTimestamp(c);
        return t == null ? null : t.toLocalDateTime();
    }

    private static Long longOrNull(ResultSet rs, String c) throws SQLException {
        long v = rs.getLong(c);
        return rs.wasNull() ? null : v;
    }

    private static Integer intOrNull(ResultSet rs, String c) throws SQLException {
        int v = rs.getInt(c);
        return rs.wasNull() ? null : v;
    }

    private static Boolean boolOrNull(ResultSet rs, String c) throws SQLException {
        boolean v = rs.getBoolean(c);
        return rs.wasNull() ? null : v;
    }
}
