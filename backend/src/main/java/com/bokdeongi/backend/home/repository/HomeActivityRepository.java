package com.bokdeongi.backend.home.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * HOME-02 신규 해당 배지 및 주간 요약 조회 Repository.
 */
@Repository
public class HomeActivityRepository {

    private final JdbcTemplate jdbcTemplate;

    public HomeActivityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 현재 프로필 버전에서 적격 판정을 받은 혜택 조회.
     */
    public List<EligibleActivityRow> findEligibleActivities(long userId) {
        String sql = """
                SELECT
                    b.id AS benefit_id,
                    b.title,
                    er.previous_result,
                    er.first_eligible_at,
                    er.seen_at,
                    er.evaluated_at,
                    b.apply_end,
                    b.last_changed_at
                FROM eligibility_results er
                JOIN users u ON u.id = er.user_id
                JOIN benefits b ON b.id = er.benefit_id
                WHERE er.user_id = ?
                  AND er.profile_version = u.profile_version
                  AND er.result = 'ELIGIBLE'
                  AND b.status = 'ACTIVE'
                ORDER BY b.id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new EligibleActivityRow(
                        rs.getLong("benefit_id"),
                        rs.getString("title"),
                        rs.getString("previous_result"),
                        rs.getObject("first_eligible_at", LocalDateTime.class),
                        rs.getObject("seen_at", LocalDateTime.class),
                        rs.getObject("evaluated_at", LocalDateTime.class),
                        rs.getObject("apply_end", LocalDate.class),
                        rs.getObject("last_changed_at", LocalDateTime.class)
                ), userId);
    }

    /**
     * 사용자 마지막 로그인 시각 조회.
     */
    public LocalDateTime findLastLoginAt(long userId) {
        String sql = """
                SELECT last_login_at
                FROM users
                WHERE id = ?
                """;

        List<LocalDateTime> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) ->
                        rs.getObject("last_login_at", LocalDateTime.class),
                userId
        );

        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * HOME-02 조회용 적격 혜택 데이터.
     */
    public record EligibleActivityRow(
            Long benefitId,
            String title,
            String previousResult,
            LocalDateTime firstEligibleAt,
            LocalDateTime seenAt,
            LocalDateTime evaluatedAt,
            LocalDate applyEnd,
            LocalDateTime lastChangedAt
    ) {
    }

    /**
     * 현재 사용자의 적격 혜택을 확인 처리한다.
     *
     * @return 갱신된 행 수
     */
    public int markBenefitAsSeen(long userId, long benefitId) {
        String sql = """
            UPDATE eligibility_results er
            JOIN users u ON u.id = er.user_id
            JOIN benefits b ON b.id = er.benefit_id
            SET er.seen_at = CURRENT_TIMESTAMP(6)
            WHERE er.user_id = ?
              AND er.benefit_id = ?
              AND er.profile_version = u.profile_version
              AND er.result = 'ELIGIBLE'
              AND b.status = 'ACTIVE'
              AND er.seen_at IS NULL
            """;

        return jdbcTemplate.update(sql, userId, benefitId);
    }
}