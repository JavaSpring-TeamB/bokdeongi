package com.bokdeongi.backend.home.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * HOME-03 신청 일정 캘린더 조회 Repository.
 *
 * 관심 또는 진행 중인 혜택의 일정과
 * 대체 표시용 마감 임박 혜택을 조회한다.
 */
@Repository
public class HomeCalendarRepository {

    private final JdbcTemplate jdbcTemplate;

    public HomeCalendarRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 사용자의 관심·진행 중 혜택 조회.
     */
    public List<CalendarBenefitRow> findUserBenefits(long userId) {
        String sql = """
                SELECT b.id, b.title,
                       b.apply_start, b.apply_end, b.result_date,
                       b.date_status, b.date_precision
                FROM user_benefits ub
                JOIN benefits b ON b.id = ub.benefit_id
                WHERE ub.user_id = ?
                  AND b.status = 'ACTIVE'
                  AND (
                      ub.bookmarked = 1
                      OR ub.app_status IN ('IN_PROGRESS', 'SUBMITTED')
                  )
                ORDER BY b.id
                """;

        return jdbcTemplate.query(sql, this::mapRow, userId);
    }

    /**
     * 관심·진행 중 혜택이 없을 때 표시할
     * 마감 임박 혜택 조회.
     */
    public List<CalendarBenefitRow> findUpcomingDeadlines(
            LocalDate startDate,
            LocalDate endDate
    ) {
        String sql = """
                SELECT b.id, b.title,
                       b.apply_start, b.apply_end, b.result_date,
                       b.date_status, b.date_precision
                FROM benefits b
                WHERE b.status = 'ACTIVE'
                  AND b.apply_end BETWEEN ? AND ?
                  AND b.date_status = 'PARSED'
                  AND b.date_precision = 'DAY'
                ORDER BY b.apply_end ASC, b.id ASC
                LIMIT 10
                """;

        return jdbcTemplate.query(
                sql,
                this::mapRow,
                startDate,
                endDate
        );
    }

    /**
     * SQL 조회 결과를 캘린더 일정 구성에 필요한
     * 혜택 데이터 객체로 변환한다.
     */
    private CalendarBenefitRow mapRow(
            java.sql.ResultSet rs,
            int rowNum
    ) throws java.sql.SQLException {
        return new CalendarBenefitRow(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getObject("apply_start", LocalDate.class),
                rs.getObject("apply_end", LocalDate.class),
                rs.getObject("result_date", LocalDate.class),
                rs.getString("date_status"),
                rs.getString("date_precision")
        );
    }

    /**
     * 캘린더 일정 구성에 필요한 혜택 데이터.
     */
    public record CalendarBenefitRow(
            Long benefitId,
            String title,
            LocalDate applyStart,
            LocalDate applyEnd,
            LocalDate resultDate,
            String dateStatus,
            String datePrecision
    ) {
    }
}