package com.bokdeongi.backend.eligibility.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 회원 × 혜택 판정 결과 (eligibility_results). 판정 엔진 결과와 이름이 겹치지 않게 Entity를 붙였다.
 *
 * <p>쓰기는 {@code EligibilityService}(일괄 판정)가 JDBC로 한다. 이 엔티티는 목록·상세·홈 화면 API가 읽고,
 * 사용자가 확인한 시각(seen_at)을 남길 때 쓴다.
 */
@Entity
@Table(name = "eligibility_results",
        uniqueConstraints = @UniqueConstraint(name = "uk_eligibility_results_user_benefit",
                columnNames = {"user_id", "benefit_id"}))
public class EligibilityResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "benefit_id", nullable = false)
    private Long benefitId;

    /** ELIGIBLE / CONDITIONAL / INELIGIBLE */
    @Column(name = "result", length = 15, nullable = false)
    private String result;

    /** 직전 판정 (HOME-02 신규 해당 배지: previous_result != ELIGIBLE 이고 result = ELIGIBLE) */
    @Column(name = "previous_result", length = 15)
    private String previousResult;

    @Column(name = "near_miss", nullable = false)
    private Boolean nearMiss = false;

    @Column(name = "near_miss_reason", length = 200)
    private String nearMissReason;

    /** 조건부가 된 이유 = 더 물어볼 판정 필드 목록. 예: ["INCOME_PCT"] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_fields", columnDefinition = "json")
    private String missingFields;

    /** 조건별 근거 [{ruleId, field, expected, actual, outcome, reason, sourceQuote, boundary, group}] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trace", columnDefinition = "json", nullable = false)
    private String trace;

    @Column(name = "profile_version", nullable = false)
    private Integer profileVersion;

    @Column(name = "first_eligible_at")
    private LocalDateTime firstEligibleAt;

    @Column(name = "seen_at")
    private LocalDateTime seenAt;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    protected EligibilityResultEntity() {
    }

    /** 사용자가 결과를 확인했다 (신규 배지 끄기). */
    public void markSeen(LocalDateTime when) {
        this.seenAt = when;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBenefitId() {
        return benefitId;
    }

    public String getResult() {
        return result;
    }

    public String getPreviousResult() {
        return previousResult;
    }

    public Boolean getNearMiss() {
        return nearMiss;
    }

    public String getNearMissReason() {
        return nearMissReason;
    }

    public String getMissingFields() {
        return missingFields;
    }

    public String getTrace() {
        return trace;
    }

    public Integer getProfileVersion() {
        return profileVersion;
    }

    public LocalDateTime getFirstEligibleAt() {
        return firstEligibleAt;
    }

    public LocalDateTime getSeenAt() {
        return seenAt;
    }

    public LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }
}
