package com.bokdeongi.backend.auth.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 비밀번호 재설정 인증 정보를 관리하는 JPA Entity.
 *
 * 회원별 이메일 인증번호, 재설정 토큰,
 * 유효기간 및 인증 실패 횟수를 관리한다.
 *
 * 인증번호와 재설정 토큰은 평문으로 저장하지 않는다.
 */
@Entity
@Table(
        name = "password_reset_tokens",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_password_reset_user",
                        columnNames = "user_id"
                ),
                @UniqueConstraint(
                        name = "uk_password_reset_token",
                        columnNames = "reset_token_hash"
                )
        }
)
public class PasswordResetToken {

    // 인증 기록 고유 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 비밀번호 재설정을 요청한 회원
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_password_reset_user"
            )
    )
    private User user;

    // 이메일 인증번호 해시
    @Column(name = "code_hash", length = 255, nullable = false)
    private String codeHash;

    // 이메일 인증번호 만료 시각
    @Column(name = "code_expires_at", nullable = false)
    private LocalDateTime codeExpiresAt;

    // 비밀번호 재설정 토큰 해시
    @Column(name = "reset_token_hash", length = 255)
    private String resetTokenHash;

    // 비밀번호 재설정 토큰 만료 시각
    @Column(name = "reset_expires_at")
    private LocalDateTime resetExpiresAt;

    // 인증번호 입력 실패 횟수
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    // 이메일 인증 완료 시각
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    // 비밀번호 재설정 토큰 사용 시각
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    // 인증번호 발급 시각
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * JPA에서 사용하는 기본 생성자.
     */
    protected PasswordResetToken() {
    }

    /**
     * 새로운 비밀번호 재설정 인증 기록을 생성한다.
     */
    public PasswordResetToken(
            User user,
            String codeHash,
            LocalDateTime codeExpiresAt,
            LocalDateTime now
    ) {
        this.user = user;
        reissue(codeHash, codeExpiresAt, now);
    }

    /* ==================== 인증 정보 관리 ==================== */

    /**
     * 인증번호를 재발급한다.
     *
     * 기존 인증번호와 재설정 토큰을 무효화하고
     * 인증 실패 횟수 및 사용 상태를 초기화한다.
     */
    public void reissue(
            String codeHash,
            LocalDateTime codeExpiresAt,
            LocalDateTime now
    ) {
        this.codeHash = codeHash;
        this.codeExpiresAt = codeExpiresAt;
        this.resetTokenHash = null;
        this.resetExpiresAt = null;
        this.failedAttempts = 0;
        this.verifiedAt = null;
        this.usedAt = null;
        this.createdAt = now;
    }

    /**
     * 인증번호가 만료되었는지 확인한다.
     * 만료 시각과 같거나 이후이면 만료된 것으로 판단한다.
     */
    public boolean isCodeExpired(LocalDateTime now) {
        return !now.isBefore(codeExpiresAt);
    }

    /**
     * 인증번호 입력 실패 횟수를 증가시킨다.
     */
    public void increaseFailedAttempts() {
        this.failedAttempts++;
    }

    /**
     * 인증번호 입력 실패 제한에 도달했는지 확인한다.
     */
    public boolean hasReachedMaxAttempts(int maxAttempts) {
        return failedAttempts >= maxAttempts;
    }

    /**
     * 인증번호 재발급이 가능한 시각인지 확인한다.
     */
    public boolean canResend(
            LocalDateTime now,
            long resendIntervalSeconds
    ) {
        return !now.isBefore(
                createdAt.plusSeconds(resendIntervalSeconds)
        );
    }

    /**
     * 이메일 인증이 완료되었는지 확인한다.
     */
    public boolean isVerified() {
        return verifiedAt != null;
    }

    /**
     * 이메일 인증 완료 후 비밀번호 재설정 토큰을 등록한다.
     */
    public void markVerified(
            String resetTokenHash,
            LocalDateTime resetExpiresAt,
            LocalDateTime now
    ) {
        if (isVerified() || isUsed()) {
            throw new IllegalStateException(
                    "이미 처리된 인증 요청입니다."
            );
        }

        this.verifiedAt = now;
        this.resetTokenHash = resetTokenHash;
        this.resetExpiresAt = resetExpiresAt;
    }

    /**
     * 비밀번호 재설정 토큰의 만료 여부를 확인한다.
     */
    public boolean isResetTokenExpired(LocalDateTime now) {
        return resetExpiresAt == null
                || !now.isBefore(resetExpiresAt);
    }

    /**
     * 비밀번호 재설정 토큰의 사용 여부를 확인한다.
     */
    public boolean isUsed() {
        return usedAt != null;
    }

    /**
     * 비밀번호 재설정이 완료된 인증 기록을 사용 처리한다.
     */
    public void markUsed(LocalDateTime now) {
        this.usedAt = now;
        this.resetTokenHash = null;
    }

    /* ==================== Getter ==================== */

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public LocalDateTime getCodeExpiresAt() {
        return codeExpiresAt;
    }

    public String getResetTokenHash() {
        return resetTokenHash;
    }

    public LocalDateTime getResetExpiresAt() {
        return resetExpiresAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}