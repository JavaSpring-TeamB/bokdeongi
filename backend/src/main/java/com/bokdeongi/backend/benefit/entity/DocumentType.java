package com.bokdeongi.backend.benefit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 서류 목록 (document_types, DATA-07). 데이터는 data-lab/documents/document_types.sql로 넣는다.
 *
 * <p>서류 준비(ASST-04)·서류 검토(ASST-03)·서류 유효기간(ASST-07)이 함께 쓴다. aliases는 JSON 원문 문자열로 둔다
 * (Benefit과 같은 방식, 읽을 때 JsonText).
 */
@Entity
@Table(name = "document_types")
public class DocumentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 50, nullable = false, unique = true)
    private String name;

    /** ["등본","주민등록표 등본"] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "aliases", columnDefinition = "json")
    private String aliases;

    @Column(name = "issuer", length = 100)
    private String issuer;

    @Column(name = "online_available")
    private Boolean onlineAvailable;

    @Column(name = "issue_url", length = 500)
    private String issueUrl;

    @Column(name = "issue_method", length = 65535)
    private String issueMethod;

    @Column(name = "default_validity_days")
    private Integer defaultValidityDays;

    @Column(name = "alternatives", length = 65535)
    private String alternatives;

    /** OCR로 읽는 서류 (D-18의 4종) */
    @Column(name = "ocr_supported", nullable = false)
    private Boolean ocrSupported = false;

    protected DocumentType() {
    }

    public DocumentType(String name, String aliases, String issuer, Boolean onlineAvailable, String issueUrl,
                        String issueMethod, Integer defaultValidityDays, String alternatives, Boolean ocrSupported) {
        this.name = name;
        this.aliases = aliases;
        this.issuer = issuer;
        this.onlineAvailable = onlineAvailable;
        this.issueUrl = issueUrl;
        this.issueMethod = issueMethod;
        this.defaultValidityDays = defaultValidityDays;
        this.alternatives = alternatives;
        this.ocrSupported = ocrSupported != null && ocrSupported;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAliases() {
        return aliases;
    }

    public String getIssuer() {
        return issuer;
    }

    public Boolean getOnlineAvailable() {
        return onlineAvailable;
    }

    public String getIssueUrl() {
        return issueUrl;
    }

    public String getIssueMethod() {
        return issueMethod;
    }

    public Integer getDefaultValidityDays() {
        return defaultValidityDays;
    }

    public String getAlternatives() {
        return alternatives;
    }

    public Boolean getOcrSupported() {
        return ocrSupported;
    }
}
