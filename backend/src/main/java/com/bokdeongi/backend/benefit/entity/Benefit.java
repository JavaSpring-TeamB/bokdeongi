package com.bokdeongi.backend.benefit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 혜택 공고 (benefits). 공공서비스 API 원문 + 지원조건 + 가공값(날짜·금액·서류·해시태그) + AI 요약.
 *
 * <p>컬럼 정의는 노션 테이블 정의서 benefits와 공용 DB 스키마(v2.10)를 그대로 따른다. JSON 칸은 원문 문자열로 둔다
 * (users와 같은 방식). 혜택 상세(BEN-06)·관심(BEN-07)·검색(BEN-11)·동기화(DATA-01)가 함께 쓴다.
 * created_at·updated_at은 DB 기본값으로 채워져 매핑하지 않는다. text 칸은 length = 65535로 적었다(MySQL text와 같은 크기,
 * 테스트용 H2에서도 만들어지게).
 */
@Entity
@Table(name = "benefits")
public class Benefit {

    /** 목록에 나오는 상태. ENDED(API에서 사라짐)·EXPIRED(신청 기간 지남)는 목록에서 뺀다 (D-85) */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED = "ENDED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_id", length = 20, nullable = false, unique = true)
    private String serviceId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "purpose_text", length = 65535)
    private String purposeText;

    @Column(name = "support_type", length = 30)
    private String supportType;

    @Column(name = "target_text", length = 65535)
    private String targetText;

    @Column(name = "criteria_text", length = 65535)
    private String criteriaText;

    @Column(name = "content_text", length = 65535)
    private String contentText;

    @Column(name = "apply_method_text", length = 65535)
    private String applyMethodText;

    @Column(name = "apply_period_text", length = 65535)
    private String applyPeriodText;

    @Column(name = "required_docs_text", length = 65535)
    private String requiredDocsText;

    @Column(name = "official_docs_text", length = 65535)
    private String officialDocsText;

    @Column(name = "self_docs_text", length = 65535)
    private String selfDocsText;

    @Column(name = "org_name", length = 255)
    private String orgName;

    @Column(name = "dept_name", length = 255)
    private String deptName;

    @Column(name = "receiving_org", length = 255)
    private String receivingOrg;

    @Column(name = "contact", length = 255)
    private String contact;

    @Column(name = "online_apply_url", length = 500)
    private String onlineApplyUrl;

    @Column(name = "detail_url", length = 500)
    private String detailUrl;

    @Column(name = "service_field", length = 30)
    private String serviceField;

    @Column(name = "user_type", length = 30)
    private String userType;

    @Column(name = "view_count")
    private Integer viewCount;

    @Column(name = "api_modified_at")
    private LocalDateTime apiModifiedAt;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "sync_error", length = 65535)
    private String syncError;

    @Column(name = "status", length = 10, nullable = false)
    private String status = STATUS_ACTIVE;

    @Column(name = "age_min")
    private Short ageMin;

    @Column(name = "age_max")
    private Short ageMax;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "y_codes", columnDefinition = "json")
    private String yCodes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions_raw", columnDefinition = "json")
    private String conditionsRaw;

    @Column(name = "target_scope", length = 10)
    private String targetScope;

    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(name = "rules_status", length = 10)
    private String rulesStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unparsed_conditions", columnDefinition = "json")
    private String unparsedConditions;

    @Column(name = "code_conflict", nullable = false)
    private Boolean codeConflict = false;

    @Column(name = "apply_start")
    private LocalDate applyStart;

    @Column(name = "apply_end")
    private LocalDate applyEnd;

    @Column(name = "result_date")
    private LocalDate resultDate;

    @Column(name = "date_status", length = 15)
    private String dateStatus;

    @Column(name = "date_precision", length = 5)
    private String datePrecision;

    @Column(name = "date_source_quote", length = 65535)
    private String dateSourceQuote;

    @Column(name = "recurring_yearly", nullable = false)
    private Boolean recurringYearly = false;

    @Column(name = "max_amount")
    private Integer maxAmount;

    @Column(name = "amount_period", length = 10)
    private String amountPeriod;

    @Column(name = "annual_amount")
    private Integer annualAmount;

    @Column(name = "amount_confidence", precision = 3, scale = 2)
    private BigDecimal amountConfidence;

    @Column(name = "amount_source_quote", length = 65535)
    private String amountSourceQuote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "amount_detail", columnDefinition = "json")
    private String amountDetail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exclusions", columnDefinition = "json")
    private String exclusions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documents", columnDefinition = "json")
    private String documents;

    @Column(name = "online_apply")
    private Boolean onlineApply;

    @Column(name = "self_doc_count")
    private Short selfDocCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hashtags", columnDefinition = "json")
    private String hashtags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hashtag_candidates", columnDefinition = "json")
    private String hashtagCandidates;

    @Column(name = "summary_who", length = 100)
    private String summaryWho;

    @Column(name = "summary_what", length = 100)
    private String summaryWhat;

    @Column(name = "summary_how", length = 100)
    private String summaryHow;

    @Column(name = "summary_when", length = 100)
    private String summaryWhen;

    @Column(name = "easy_text", length = 65535)
    private String easyText;

    @Column(name = "ai_status", length = 10)
    private String aiStatus;

    @Column(name = "ai_source_modified_at")
    private LocalDateTime aiSourceModifiedAt;

    @Column(name = "ai_generated_at")
    private LocalDateTime aiGeneratedAt;

    @Column(name = "last_change_summary", length = 200)
    private String lastChangeSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_changed_fields", columnDefinition = "json")
    private String lastChangedFields;

    @Column(name = "last_changed_at")
    private LocalDateTime lastChangedAt;
    protected Benefit() {
    }

    public Long getId() {
        return id;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getTitle() {
        return title;
    }

    public String getPurposeText() {
        return purposeText;
    }

    public String getSupportType() {
        return supportType;
    }

    public String getTargetText() {
        return targetText;
    }

    public String getCriteriaText() {
        return criteriaText;
    }

    public String getContentText() {
        return contentText;
    }

    public String getApplyMethodText() {
        return applyMethodText;
    }

    public String getApplyPeriodText() {
        return applyPeriodText;
    }

    public String getRequiredDocsText() {
        return requiredDocsText;
    }

    public String getOfficialDocsText() {
        return officialDocsText;
    }

    public String getSelfDocsText() {
        return selfDocsText;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getDeptName() {
        return deptName;
    }

    public String getReceivingOrg() {
        return receivingOrg;
    }

    public String getContact() {
        return contact;
    }

    public String getOnlineApplyUrl() {
        return onlineApplyUrl;
    }

    public String getDetailUrl() {
        return detailUrl;
    }

    public String getServiceField() {
        return serviceField;
    }

    public String getUserType() {
        return userType;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public LocalDateTime getApiModifiedAt() {
        return apiModifiedAt;
    }

    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }

    public String getSyncError() {
        return syncError;
    }

    public String getStatus() {
        return status;
    }

    public Short getAgeMin() {
        return ageMin;
    }

    public Short getAgeMax() {
        return ageMax;
    }

    public String getYCodes() {
        return yCodes;
    }

    public String getConditionsRaw() {
        return conditionsRaw;
    }

    public String getTargetScope() {
        return targetScope;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getRulesStatus() {
        return rulesStatus;
    }

    public String getUnparsedConditions() {
        return unparsedConditions;
    }

    public Boolean getCodeConflict() {
        return codeConflict;
    }

    public LocalDate getApplyStart() {
        return applyStart;
    }

    public LocalDate getApplyEnd() {
        return applyEnd;
    }

    public LocalDate getResultDate() {
        return resultDate;
    }

    public String getDateStatus() {
        return dateStatus;
    }

    public String getDatePrecision() {
        return datePrecision;
    }

    public String getDateSourceQuote() {
        return dateSourceQuote;
    }

    public Boolean getRecurringYearly() {
        return recurringYearly;
    }

    public Integer getMaxAmount() {
        return maxAmount;
    }

    public String getAmountPeriod() {
        return amountPeriod;
    }

    public Integer getAnnualAmount() {
        return annualAmount;
    }

    public BigDecimal getAmountConfidence() {
        return amountConfidence;
    }

    public String getAmountSourceQuote() {
        return amountSourceQuote;
    }

    public String getAmountDetail() {
        return amountDetail;
    }

    public String getExclusions() {
        return exclusions;
    }

    public String getDocuments() {
        return documents;
    }

    public Boolean getOnlineApply() {
        return onlineApply;
    }

    public Short getSelfDocCount() {
        return selfDocCount;
    }

    public String getHashtags() {
        return hashtags;
    }

    public String getHashtagCandidates() {
        return hashtagCandidates;
    }

    public String getSummaryWho() {
        return summaryWho;
    }

    public String getSummaryWhat() {
        return summaryWhat;
    }

    public String getSummaryHow() {
        return summaryHow;
    }

    public String getSummaryWhen() {
        return summaryWhen;
    }

    public String getEasyText() {
        return easyText;
    }

    public String getAiStatus() {
        return aiStatus;
    }

    public LocalDateTime getAiSourceModifiedAt() {
        return aiSourceModifiedAt;
    }

    public LocalDateTime getAiGeneratedAt() {
        return aiGeneratedAt;
    }

    public String getLastChangeSummary() {
        return lastChangeSummary;
    }

    public String getLastChangedFields() {
        return lastChangedFields;
    }

    public LocalDateTime getLastChangedAt() {
        return lastChangedAt;
    }

    /**
     * 쉬운 말·3줄 요약 저장 (EASY-01). 실패하면 status = FAILED, 글은 비운다.
     * ai_source_modified_at에 그때의 api_modified_at을 남겨, 공고가 바뀌면 다시 만들게 한다.
     */
    public void applyEasy(String status, String who, String what, String how, String when, String text,
                          LocalDateTime generatedAt) {
        this.aiStatus = status;
        this.summaryWho = who;
        this.summaryWhat = what;
        this.summaryHow = how;
        this.summaryWhen = when;
        this.easyText = text;
        this.aiSourceModifiedAt = this.apiModifiedAt;
        this.aiGeneratedAt = generatedAt;
    }
}
