package com.bokdeongi.backend.benefit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 판정 규칙 한 줄 = 조건 하나 (benefit_rules). 같은 rule_group은 '그리고', 그룹끼리는 '또는', 0번은 항상 '그리고' (D-62).
 * 지원조건 코드 변환(DATA-02, source = CODE)과 LLM 구조화(DATA-03, source = LLM)가 같은 모양으로 저장한다.
 */
@Entity
@Table(name = "benefit_rules")
public class BenefitRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "benefit_id", nullable = false)
    private Long benefitId;

    @Column(name = "rule_group", nullable = false)
    private Short ruleGroup = 0;

    /** AGE, GENDER, REGION, ANSWER:{key}, JA_GROUP:{JA03} … (테이블 정의서 판정 필드 목록) */
    @Column(name = "field", length = 30, nullable = false)
    private String field;

    /** EQ / IN / LTE / GTE / BETWEEN */
    @Column(name = "operator", length = 10, nullable = false)
    private String operator;

    /** JSON 배열 원문. 예: [19, 34], ["JA0317","JA0327"]. VALUE는 H2 예약어라 따옴표로 감싼다(테스트 DB) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "`value`", columnDefinition = "json", nullable = false)
    private String value;

    /** CODE / LLM / MANUAL */
    @Column(name = "source", length = 10, nullable = false)
    private String source;

    @Column(name = "source_quote", length = 65535)
    private String sourceQuote;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "verified", nullable = false)
    private Boolean verified = false;

    @Column(name = "source_modified_at")
    private LocalDateTime sourceModifiedAt;

    protected BenefitRule() {
    }

    public BenefitRule(Long benefitId, short ruleGroup, String field, String operator, String value, String source,
            String sourceQuote, BigDecimal confidence, LocalDateTime sourceModifiedAt) {
        this.benefitId = benefitId;
        this.ruleGroup = ruleGroup;
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.source = source;
        this.sourceQuote = sourceQuote;
        this.confidence = confidence;
        this.sourceModifiedAt = sourceModifiedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getBenefitId() {
        return benefitId;
    }

    public Short getRuleGroup() {
        return ruleGroup;
    }

    public String getField() {
        return field;
    }

    public String getOperator() {
        return operator;
    }

    public String getValue() {
        return value;
    }

    public String getSource() {
        return source;
    }

    public String getSourceQuote() {
        return sourceQuote;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public Boolean getVerified() {
        return verified;
    }

    public LocalDateTime getSourceModifiedAt() {
        return sourceModifiedAt;
    }
}
