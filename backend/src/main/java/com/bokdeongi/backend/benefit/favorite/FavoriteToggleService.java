package com.bokdeongi.backend.benefit.favorite;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 회원별 혜택 관심 저장 상태를 관리하는 서비스.
 *
 * 관심 등록, 해제 및 현재 저장 여부를 조회한다.
 * 기존 자격 판정 정보는 유지하고 관심 관련 필드만 변경한다.
 */
@Service
public class FavoriteToggleService {

    private final JdbcTemplate jdbc;

    public FavoriteToggleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 혜택을 관심 목록에 등록한다.
     *
     * 이미 등록된 혜택은 기존 등록 시각을 유지하며,
     * 해제 후 다시 등록하는 경우 등록 시각을 갱신한다.
     *
     * @return 관심 등록 상태인 true
     */
    @Transactional
    public boolean add(long userId, long benefitId) {
        validateBenefit(benefitId);

        jdbc.update("""
                INSERT INTO user_benefits
                    (user_id, benefit_id, bookmarked, bookmarked_at)
                VALUES (?, ?, TRUE, UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE
                    bookmarked_at = IF(
                        bookmarked = TRUE,
                        bookmarked_at,
                        UTC_TIMESTAMP(6)
                    ),
                    bookmarked = TRUE
                """, userId, benefitId);

        return true;
    }

    /**
     * 혜택의 관심 저장 상태를 해제한다.
     *
     * 기존 자격 판정 정보 유지를 위해 행을 삭제하지 않고
     * 관심 저장 여부와 등록 시각만 변경한다.
     *
     * @return 관심 해제 상태인 false
     */
    @Transactional
    public boolean remove(long userId, long benefitId) {
        validateBenefit(benefitId);

        jdbc.update("""
                UPDATE user_benefits
                SET bookmarked = FALSE,
                    bookmarked_at = NULL
                WHERE user_id = ?
                  AND benefit_id = ?
                  AND bookmarked = TRUE
                """, userId, benefitId);

        return false;
    }

    /**
     * 회원의 현재 혜택 관심 저장 여부를 조회한다.
     * 관련 기록이 없으면 false를 반환한다.
     */
    @Transactional(readOnly = true)
    public boolean isBookmarked(long userId, long benefitId) {
        validateBenefit(benefitId);

        Boolean bookmarked = jdbc.query("""
                SELECT bookmarked
                FROM user_benefits
                WHERE user_id = ?
                  AND benefit_id = ?
                """, rs -> rs.next()
                ? rs.getBoolean("bookmarked")
                : false, userId, benefitId);

        return Boolean.TRUE.equals(bookmarked);
    }

    /**
     * 요청한 혜택의 존재 여부를 검증한다.
     * 존재하지 않는 혜택은 404 오류로 처리한다.
     */
    private void validateBenefit(long benefitId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM benefits
                WHERE id = ?
                """, Integer.class, benefitId);

        if (count == null || count == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "존재하지 않는 혜택입니다."
            );
        }
    }
}