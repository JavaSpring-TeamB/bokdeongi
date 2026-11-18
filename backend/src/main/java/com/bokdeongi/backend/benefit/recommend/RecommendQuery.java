package com.bokdeongi.backend.benefit.recommend;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 맞춤 혜택 목록 조회 (BEN-01). 판정 결과(eligibility_results)와 공고(benefits)를 묶어 탭·필터·정렬·페이지로 읽는다.
 *
 * <p>목록에서 빼는 공고: 신청 기간이 지났거나 API에서 사라진 공고(status가 ACTIVE가 아님, D-85),
 * 시설·기업 대상(target_scope = CORP, D-49·D-87). 정렬·탭 SQL 조각은 enum의 고정 문자열이고 값은 모두 ? 로 넘긴다.
 */
@Repository
public class RecommendQuery {

    /** 지역 필터에서 소관기관 지역이 없는(중앙행정기관 등) 공고 */
    public static final String NATIONWIDE = "전국";

    private static final String FROM = """
            FROM eligibility_results e
            JOIN benefits b ON b.id = e.benefit_id
            LEFT JOIN regions rg ON rg.region_code = b.region_code
            LEFT JOIN user_benefits ub ON ub.user_id = e.user_id AND ub.benefit_id = e.benefit_id
            WHERE e.user_id = ? AND b.status = 'ACTIVE' AND (b.target_scope IS NULL OR b.target_scope <> 'CORP')
            """;

    private final JdbcTemplate jdbc;

    public RecommendQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 필터: null이면 전체. region은 시·도 이름 또는 "전국", field는 서비스 분야(benefits.service_field) */
    public record Filter(String region, String field) {
    }

    public List<RecommendRow> page(long userId, RecommendTab tab, Filter filter, RecommendSort sort, int page, int size) {
        List<Object> args = new ArrayList<>();
        args.add(userId);
        String sql = SELECT + FROM + " AND " + tab.where + filterSql(filter, args)
                + " ORDER BY " + sort.orderBy + " LIMIT ? OFFSET ?";
        args.add(size);
        args.add((long) page * size);
        return jdbc.query(sql, (rs, n) -> row(rs), args.toArray());
    }

    /** 카드 한 장 (추가 질문에 답한 뒤 다시 그릴 때). 목록에서 빠지는 공고면 비어 있다 */
    public Optional<RecommendRow> one(long userId, long benefitId) {
        return jdbc.query(SELECT + FROM + " AND b.id = ?", (rs, n) -> row(rs), userId, benefitId).stream().findFirst();
    }

    private static final String SELECT = """
            SELECT b.id, b.title, b.service_field, b.support_type, COALESCE(b.summary_what, b.purpose_text) AS summary,
                       b.max_amount, b.amount_period, b.annual_amount, b.apply_start, b.apply_end, b.date_status,
                       b.date_precision, b.recurring_yearly, b.online_apply, b.self_doc_count, b.hashtags, b.detail_url,
                       b.online_apply_url, b.org_name, COALESCE(rg.sido_name, '전국') AS region_name,
                       e.result, e.previous_result, e.near_miss, e.near_miss_reason, e.missing_fields, e.trace,
                       COALESCE(ub.bookmarked, FALSE) AS bookmarked
            """;

    private static RecommendRow row(ResultSet rs) throws SQLException {
        return new RecommendRow(rs.getLong("id"), rs.getString("title"),
                rs.getString("service_field"), rs.getString("support_type"), rs.getString("summary"),
                longOrNull(rs, "max_amount"), rs.getString("amount_period"), longOrNull(rs, "annual_amount"),
                localDate(rs, "apply_start"), localDate(rs, "apply_end"), rs.getString("date_status"),
                rs.getString("date_precision"), rs.getBoolean("recurring_yearly"), boolOrNull(rs, "online_apply"),
                intOrNull(rs, "self_doc_count"), rs.getString("hashtags"), rs.getString("detail_url"),
                rs.getString("online_apply_url"), rs.getString("org_name"), rs.getString("region_name"),
                rs.getString("result"), rs.getString("previous_result"), rs.getBoolean("near_miss"),
                rs.getString("near_miss_reason"), rs.getString("missing_fields"), rs.getString("trace"),
                rs.getBoolean("bookmarked"));
    }

    /** 탭별 건수 (필터 적용) */
    public Map<RecommendTab, Long> counts(long userId, Filter filter) {
        List<Object> args = new ArrayList<>();
        args.add(userId);
        StringBuilder select = new StringBuilder("SELECT ");
        RecommendTab[] tabs = RecommendTab.values();
        for (int i = 0; i < tabs.length; i++) {
            select.append(i == 0 ? "" : ", ").append("COALESCE(SUM(CASE WHEN ").append(tabs[i].where)
                    .append(" THEN 1 ELSE 0 END), 0) AS c").append(i);
        }
        String sql = select + " " + FROM + filterSql(filter, args);
        Map<RecommendTab, Long> out = new EnumMap<>(RecommendTab.class);
        jdbc.query(sql, (rs, n) -> {
            for (int i = 0; i < tabs.length; i++) {
                out.put(tabs[i], rs.getLong("c" + i));
            }
            return null;
        }, args.toArray());
        return out;
    }

    /** 이 탭에서 고를 수 있는 지역·분야 (필터 없이) */
    public FilterOptions options(long userId, RecommendTab tab) {
        TreeSet<String> regions = new TreeSet<>();
        TreeSet<String> fields = new TreeSet<>();
        jdbc.query("SELECT DISTINCT COALESCE(rg.sido_name, '전국') AS region_name, b.service_field "
                + FROM + " AND " + tab.where, (rs, n) -> {
            regions.add(rs.getString("region_name"));
            String field = rs.getString("service_field");
            if (field != null && !field.isBlank()) {
                fields.add(field);
            }
            return null;
        }, userId);
        return new FilterOptions(List.copyOf(regions), List.copyOf(fields));
    }

    public record FilterOptions(List<String> regions, List<String> fields) {
    }

    /** 회원의 현재 프로필 버전과, 저장된 판정 결과 중 가장 오래된 버전·건수 ('업데이트 중' 표시용) */
    public record Freshness(int profileVersion, Integer oldestResultVersion, long resultCount) {
    }

    public Freshness freshness(long userId) {
        return jdbc.query("""
                SELECT u.profile_version,
                       (SELECT MIN(r.profile_version) FROM eligibility_results r WHERE r.user_id = u.id) AS oldest,
                       (SELECT COUNT(*) FROM eligibility_results r WHERE r.user_id = u.id) AS cnt
                FROM users u WHERE u.id = ?""", (rs, n) -> new Freshness(rs.getInt("profile_version"),
                intOrNull(rs, "oldest"), rs.getLong("cnt")), userId).stream().findFirst()
                .orElse(new Freshness(0, null, 0));
    }

    private static String filterSql(Filter filter, List<Object> args) {
        StringBuilder sb = new StringBuilder();
        if (filter.region() != null) {
            if (NATIONWIDE.equals(filter.region())) {
                sb.append(" AND rg.sido_name IS NULL");
            } else {
                sb.append(" AND rg.sido_name = ?");
                args.add(filter.region());
            }
        }
        if (filter.field() != null) {
            sb.append(" AND b.service_field = ?");
            args.add(filter.field());
        }
        return sb.toString();
    }

    private static java.time.LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }

    private static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    private static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static Boolean boolOrNull(ResultSet rs, String column) throws SQLException {
        boolean v = rs.getBoolean(column);
        return rs.wasNull() ? null : v;
    }
}
