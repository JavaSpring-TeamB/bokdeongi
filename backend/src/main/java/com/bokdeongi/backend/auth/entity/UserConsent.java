package com.bokdeongi.backend.auth.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 회원 약관 동의 이력을 관리하는 JPA Entity.
 *
 * user_consents 테이블과 매핑되며,
 * 약관 동의 또는 철회가 발생할 때마다 새로운 기록을 저장한다.
 */
@Entity
@Table(
        name = "user_consents",
        indexes = {
                @Index(
                        name = "idx_user_consents_user_type_acted",
                        columnList = "user_id, consent_type, acted_at"
                )
        }
)
public class UserConsent {

    // 약관 동의 이력 고유 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 동의 이력이 속한 회원.
     * 여러 동의 이력이 하나의 회원에 연결되는 다대일 관계이다.
     */
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_user_consents_user"
            )
    )
    private User user;

    // 동의 유형 (TERMS, PRIVACY, OPTIONAL_INFO, SENSITIVE, DOC_RECORD, DOC_PROCESS)
    @Column(
            name = "consent_type",
            length = 15,
            nullable = false
    )
    private String consentType;

    // 동의한 약관 문서의 버전
    @Column(
            name = "doc_version",
            length = 20,
            nullable = false
    )
    private String docVersion;

    // 동의 여부 (true: 동의, false: 철회 또는 미동의)
    @Column(name = "agreed", nullable = false)
    private Boolean agreed;

    // 동의 또는 철회가 이루어진 시각
    @Column(name = "acted_at", nullable = false)
    private LocalDateTime actedAt;

    /**
     * JPA에서 사용하는 기본 생성자.
     */
    protected UserConsent() {
    }

    /**
     * 약관 동의 이력 생성자.
     * 생성 시점의 시간을 actedAt에 기록한다.
     */
    public UserConsent(
            User user,
            String consentType,
            String docVersion,
            Boolean agreed
    ) {
        this.user = user;
        this.consentType = consentType;
        this.docVersion = docVersion;
        this.agreed = agreed;
        this.actedAt = LocalDateTime.now();
    }

    /* ==================== Getter ==================== */

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getConsentType() {
        return consentType;
    }

    public String getDocVersion() {
        return docVersion;
    }

    public Boolean getAgreed() {
        return agreed;
    }

    public LocalDateTime getActedAt() {
        return actedAt;
    }
}