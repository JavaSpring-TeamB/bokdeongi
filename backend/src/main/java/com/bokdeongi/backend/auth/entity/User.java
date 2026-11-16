package com.bokdeongi.backend.auth.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* 인증 정보 */
    @Column(name = "email", length = 100, unique = true)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "kakao_id", length = 50, unique = true)
    private String kakaoId;

    @Column(name = "nickname", length = 30)
    private String nickname;

    /* 회원가입 정보 */
    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "role", length = 10, nullable = false)
    private String role = "USER";

    @Column(name = "refresh_token_hash", length = 100)
    private String refreshTokenHash;

    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    @Column(name = "failed_login_count", nullable = false)
    private Short failedLoginCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /* 사용자 환경 설정 */
    @Column(name = "theme", length = 6, nullable = false)
    private String theme = "SYSTEM";

    @Column(name = "font_scale", nullable = false)
    private Short fontScale = 100;

    /* 기본 프로필 */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "gender", length = 1, columnDefinition = "char(1)")
    private String gender;

    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(name = "sido_name", length = 20)
    private String sidoName;

    @Column(name = "sigungu_name", length = 20)
    private String sigunguName;

    @Column(name = "household_size")
    private Short householdSize;

    /* 소득 정보 */
    @Column(name = "income_input_type", length = 10)
    private String incomeInputType;

    @Lob
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "income_amount", columnDefinition = "blob")
    private byte[] incomeAmount;

    @Column(name = "income_base_year")
    private Short incomeBaseYear;

    @Column(name = "median_income_pct", precision = 5, scale = 1)
    private BigDecimal medianIncomePct;

    /* 주거 및 가구 정보 */
    @Column(name = "housing_type", length = 10)
    private String housingType;

    @Column(name = "has_house")
    private Boolean hasHouse;

    @Column(name = "marital_status", length = 10)
    private String maritalStatus;

    @Column(name = "children_count")
    private Short childrenCount;

    @Column(name = "youngest_child_birth_year")
    private Short youngestChildBirthYear;

    @Column(name = "welfare_status", length = 12)
    private String welfareStatus;

    /* 복수 선택 및 JSON 정보 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "household_type_codes", columnDefinition = "json")
    private String householdTypeCodes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "status_codes", columnDefinition = "json")
    private String statusCodes;

    @Lob
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "sensitive_enc", columnDefinition = "blob")
    private byte[] sensitiveEnc;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers", columnDefinition = "json")
    private String answers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "doc_records", columnDefinition = "json")
    private String docRecords;

    /* 프로필 진행 상태 */
    @Column(name = "profile_version", nullable = false)
    private Integer profileVersion = 0;

    @Column(name = "profile_step", nullable = false)
    private Short profileStep = 0;

    @Column(name = "profile_completed_at")
    private LocalDateTime profileCompletedAt;

    protected User() {
    }

    /* 이메일 회원가입용 생성자 */
    public User(
            String email,
            String passwordHash,
            String name,
            String phoneNumber,
            LocalDate birthDate
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.birthDate = birthDate;
    }

    /* 카카오 회원 생성자 */
    public static User createKakaoUser(
            String kakaoId,
            String nickname,
            String email
    ) {
        User user = new User();

        user.kakaoId = kakaoId;
        user.nickname = nickname;
        user.email = email;

        return user;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getNickname() {
        return nickname;
    }

    public String getRole() {
        return role;
    }

    public Short getFailedLoginCount() {
        return failedLoginCount;
    }

    public LocalDateTime getLockedUntil() { return lockedUntil; }

    public Integer getTokenVersion() {
        return tokenVersion;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getKakaoId() { return kakaoId; }

    public void updateRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }

    public void increaseTokenVersion() { this.tokenVersion++; }

    public void clearRefreshTokenHash() { this.refreshTokenHash = null; }
}