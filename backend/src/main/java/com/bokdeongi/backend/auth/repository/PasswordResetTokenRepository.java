package com.bokdeongi.backend.auth.repository;

import com.bokdeongi.backend.auth.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 비밀번호 재설정 인증 기록을 관리하는 Repository.
 *
 * 회원별 인증 기록 및 재설정 토큰을 조회하고,
 * 쓰기 잠금을 통해 동시 인증 및 재설정 요청을 제어한다.
 */
public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    /**
     * 회원 ID로 비밀번호 재설정 인증 기록을 조회한다.
     */
    Optional<PasswordResetToken> findByUserId(Long userId);

    /**
     * 회원 ID로 인증 기록을 조회하면서 쓰기 잠금을 획득한다.
     *
     * 인증번호 재발급 및 인증 실패 횟수 변경 시 사용한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM PasswordResetToken p
            WHERE p.user.id = :userId
            """)
    Optional<PasswordResetToken> findByUserIdForUpdate(
            @Param("userId") Long userId
    );

    /**
     * 재설정 토큰 해시로 인증 기록을 조회한다.
     */
    Optional<PasswordResetToken> findByResetTokenHash(
            String resetTokenHash
    );

    /**
     * 재설정 토큰 해시로 인증 기록을 조회하면서
     * 쓰기 잠금을 획득한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM PasswordResetToken p
            WHERE p.resetTokenHash = :resetTokenHash
            """)
    Optional<PasswordResetToken> findByResetTokenHashForUpdate(
            @Param("resetTokenHash") String resetTokenHash
    );

    /**
     * 재설정 토큰 해시로 회원 ID만 조회한다.
     *
     * 회원 행을 먼저 잠근 후 인증 기록을 잠그기 위해 사용한다.
     */
    @Query("""
        SELECT p.user.id
        FROM PasswordResetToken p
        WHERE p.resetTokenHash = :resetTokenHash
        """)
    Optional<Long> findUserIdByResetTokenHash(
            @Param("resetTokenHash") String resetTokenHash
    );
}