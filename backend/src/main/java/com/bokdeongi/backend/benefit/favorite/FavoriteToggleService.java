package com.bokdeongi.backend.benefit.favorite;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FavoriteToggleService {

    private final JdbcTemplate jdbc;

    public FavoriteToggleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 관심 등록
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

    // 관심 해제
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

    // 현재 관심 여부 조회
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

    // 존재하지 않는 혜택 검증
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