package com.bokdeongi.backend.home.repository;

import java.time.LocalDate;
import java.util.List;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * HOME-01 홈 대시보드 데이터 조회 Repository
 *
 * 혜택 정보, 사용자별 자격 판정 결과 및 신청현황을
 * 데이터베이스에서 조회하는 역할을 담당한다.
 * 조회한 데이터의 집계 및 가공은 HomeService에서 처리한다.
 */
@Repository
public class HomeQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public HomeQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // HOME-01 사용자별 맞춤 혜택 판정 통계
    public EligibilityCountRow findEligibilityCounts(long userId) {
        String sql = """
                SELECT
                    COUNT(*) AS total_count,
                    COUNT(CASE WHEN er.result = 'ELIGIBLE'
                               THEN 1 END) AS eligible_count,
                    COUNT(CASE WHEN er.result = 'CONDITIONAL'
                               THEN 1 END) AS conditional_count,
                    COUNT(CASE WHEN er.result = 'INELIGIBLE'
                               THEN 1 END) AS ineligible_count
                FROM eligibility_results er
                JOIN users u ON u.id = er.user_id
                JOIN benefits b ON b.id = er.benefit_id
                WHERE er.user_id = ?
                  AND er.profile_version = u.profile_version
                  AND b.status = 'ACTIVE'
                  AND er.result IN (
                      'ELIGIBLE', 'CONDITIONAL', 'INELIGIBLE'
                  )
                """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                new EligibilityCountRow(
                        rs.getLong("total_count"),
                        rs.getLong("eligible_count"),
                        rs.getLong("conditional_count"),
                        rs.getLong("ineligible_count")
                ), userId);
    }

    // HOME-01 공고 데이터 마지막 정상 동기화 시각
    public LocalDateTime findBenefitDataUpdatedAt() {
        String sql = """
                SELECT MAX(finished_at) AS benefit_data_updated_at
                FROM sync_logs
                WHERE operation = 'SERVICE_LIST'
                  AND status = 'SUCCESS'
                """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                rs.getObject(
                        "benefit_data_updated_at",
                        LocalDateTime.class
                ));
    }

    // 사용자별 판정 결과와 혜택 정보
    public List<EligibleBenefitRow> findEligibleBenefits(long userId) {
        String sql = """
                    SELECT b.id, b.title, b.max_amount, b.annual_amount,
                           b.amount_period, b.apply_end, b.exclusions
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
                new EligibleBenefitRow(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getObject("max_amount", Long.class),
                        rs.getObject("annual_amount", Long.class),
                        rs.getString("amount_period"),
                        rs.getObject("apply_end", LocalDate.class),
                        rs.getString("exclusions")
                ), userId);
    }

    // 사용자별 신청현황
    public List<ApplicationRow> findApplications(long userId) {
        String sql = """
                SELECT
                    ub.benefit_id,
                    b.title,
                    ub.app_status,
                    ub.step
                FROM user_benefits ub
                JOIN benefits b ON b.id = ub.benefit_id
                WHERE ub.user_id = ?
                  AND ub.app_status IS NOT NULL
                ORDER BY ub.updated_at DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new ApplicationRow(
                        rs.getLong("benefit_id"),
                        rs.getString("title"),
                        rs.getString("app_status"),
                        rs.getObject("step", Short.class)
                ), userId);
    }

    // 아깝게 탈락한 혜택
    public List<NearMissRow> findNearMissBenefits(long userId) {
        String sql = """
                SELECT
                    b.id,
                    b.title,
                    er.result,
                    er.near_miss_reason
                FROM eligibility_results er
                JOIN benefits b ON b.id = er.benefit_id
                WHERE er.user_id = ?
                  AND er.near_miss = 1
                  AND b.status = 'ACTIVE'
                ORDER BY b.id
                LIMIT 5
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new NearMissRow(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("result"),
                        rs.getString("near_miss_reason")
                ), userId);
    }

    public record EligibilityCountRow(
            long totalCount,
            long eligibleCount,
            long conditionalCount,
            long ineligibleCount
    ) {
    }

    public record EligibleBenefitRow(
            Long benefitId,
            String title,
            Long maxAmount,
            Long annualAmount,
            String amountPeriod,
            LocalDate applyEnd,
            String exclusions
    ) {
    }

    public record ApplicationRow(
            Long benefitId,
            String title,
            String appStatus,
            Short step
    ) {
    }

    public record NearMissRow(
            Long benefitId,
            String title,
            String eligibilityStatus,
            String reason
    ) {
    }
}