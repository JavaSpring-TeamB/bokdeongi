package com.bokdeongi.backend.benefit.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

/**
 * 회원별 혜택 신청 정보를 관리하는 Repository.
 *
 * 신청 기록 조회, 신청 시작, 진행 상태 저장 및
 * 신청 결과 상태 변경을 SQL로 처리한다.
 *
 * 기존 관심 저장 및 자격 판정 관련 정보는 유지한다.
 */
@Repository
public class ApplicationQuery {

    private final JdbcTemplate jdbc;

    public ApplicationQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 신청 건 ID와 사용자 ID로 조회
    public Optional<ApplicationRow> findById(
            long applicationId,
            long userId
    ) {
        return jdbc.query("""
                SELECT id, user_id, benefit_id,
                       app_status, step,
                       app_answers, doc_checks,
                       started_api_modified_at
                FROM user_benefits
                WHERE id = ?
                  AND user_id = ?
                  AND app_status IS NOT NULL
                """,
                rs -> rs.next()
                        ? Optional.of(mapRow(rs))
                        : Optional.empty(),
                applicationId, userId);
    }

    // 캘린더용 조회: 신청 기록 또는 관심 저장 혜택만 허용
    public Optional<ApplicationRow> findForCalendar(
            long applicationId,
            long userId
    ) {
        return jdbc.query("""
            SELECT id, user_id, benefit_id,
                   app_status, step,
                   app_answers, doc_checks,
                   started_api_modified_at
            FROM user_benefits
            WHERE id = ?
              AND user_id = ?
              AND (
                  bookmarked = TRUE
                  OR app_status IS NOT NULL
              )
            """,
                rs -> rs.next()
                        ? Optional.of(mapRow(rs))
                        : Optional.empty(),
                applicationId,
                userId
        );
    }

    // 사용자와 혜택 ID로 기존 신청 건 조회
    public Optional<ApplicationRow> findByBenefit(
            long userId,
            long benefitId
    ) {
        return jdbc.query("""
                SELECT id, user_id, benefit_id,
                       app_status, step,
                       app_answers, doc_checks,
                       started_api_modified_at
                FROM user_benefits
                WHERE user_id = ?
                  AND benefit_id = ?
                """,
                rs -> rs.next()
                        ? Optional.of(mapRow(rs))
                        : Optional.empty(),
                userId, benefitId);
    }

    // 사용자의 전체 신청 목록 조회
    // 관심 저장만 한 혜택은 제외
    public List<ApplicationRow> findAllByUserId(long userId) {
        return jdbc.query("""
                SELECT id, user_id, benefit_id,
                       app_status, step,
                       app_answers, doc_checks,
                       started_api_modified_at
                FROM user_benefits
                WHERE user_id = ?
                  AND app_status IS NOT NULL
                ORDER BY id DESC
                """,
                (rs, rowNum) -> mapRow(rs),
                userId
        );
    }

    // 신청 시작: 기존 관심 등록 데이터는 보존
    public void start(
            long userId,
            long benefitId,
            LocalDateTime apiModifiedAt
    ) {
        jdbc.update("""
                INSERT INTO user_benefits
                    (user_id, benefit_id, app_status,
                     step, started_api_modified_at)
                VALUES (?, ?, 'IN_PROGRESS', 1, ?)
                ON DUPLICATE KEY UPDATE
                    app_status = IF(
                        app_status IS NULL,
                        'IN_PROGRESS',
                        app_status
                    ),
                    step = IF(
                        step IS NULL,
                        1,
                        step
                    ),
                    started_api_modified_at = IF(
                        app_status = 'IN_PROGRESS'
                        AND started_api_modified_at IS NULL,
                        VALUES(started_api_modified_at),
                        started_api_modified_at
                    )
                """, userId, benefitId, apiModifiedAt);
    }

    // 신청 진행 상태 자동 저장
    public boolean saveProgress(
            long applicationId,
            long userId,
            int step,
            String appAnswers,
            String docChecks
    ) {
        int updated = jdbc.update("""
                UPDATE user_benefits
                SET step = ?,
                    app_answers = CAST(? AS JSON),
                    doc_checks = CAST(? AS JSON)
                WHERE id = ?
                  AND user_id = ?
                  AND app_status = 'IN_PROGRESS'
                """,
                step,
                appAnswers,
                docChecks,
                applicationId,
                userId
        );

        return updated > 0;
    }

    // 사용자가 직접 신청 완료 또는 결과 상태 변경
    public boolean updateStatus(
            long applicationId,
            long userId,
            String currentStatus,
            String newStatus
    ) {
        int updated = jdbc.update("""
                UPDATE user_benefits
                SET app_status = ?
                WHERE id = ?
                  AND user_id = ?
                  AND app_status = ?
                """,
                newStatus,
                applicationId,
                userId,
                currentStatus
        );

        return updated > 0;
    }

    /**
     * SQL 조회 결과를 신청 정보 객체로 변환한다.
     */
    private ApplicationRow mapRow(ResultSet rs)
            throws SQLException {
        return new ApplicationRow(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("benefit_id"),
                rs.getString("app_status"),
                (Integer) rs.getObject("step"),
                rs.getString("app_answers"),
                rs.getString("doc_checks"),
                rs.getObject(
                        "started_api_modified_at",
                        LocalDateTime.class
                )
        );
    }

    /**
     * DB에서 조회한 회원별 신청 기록을 담는 객체.
     */
    public record ApplicationRow(
            long id,
            long userId,
            long benefitId,
            String appStatus,
            Integer step,
            String appAnswers,
            String docChecks,
            LocalDateTime startedApiModifiedAt
    ) {
    }
}