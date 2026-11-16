package com.bokdeongi.backend.auth.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(
            name = "consent_type",
            length = 15,
            nullable = false
    )
    private String consentType;

    @Column(
            name = "doc_version",
            length = 20,
            nullable = false
    )
    private String docVersion;

    @Column(name = "agreed", nullable = false)
    private Boolean agreed;

    @Column(name = "acted_at", nullable = false)
    private LocalDateTime actedAt;

    protected UserConsent() {
    }

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