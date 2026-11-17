package com.bokdeongi.backend.auth.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 회원 정보를 관리하는 JPA Entity.
 *
 * users 테이블과 매핑되며 인증 정보, 기본 프로필,
 * 소득·가구 정보, 사용자 환경 설정 등을 저장한다.
 */
@Entity
@Table(name = "users")
public class User {

    // 회원 고유 ID (자동 증가)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ==================== 인증 정보 ==================== */

    // 이메일 로그인 및 회원 식별에 사용
    @Column(name = "email", length = 100, unique = true)
    private String email;

    // 암호화된 비밀번호 해시
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    // 카카오 계정 고유 식별자
    @Column(name = "kakao_id", length = 50, unique = true)
    private String kakaoId;

    // 카카오 계정 닉네임
    @Column(name = "nickname", length = 30)
    private String nickname;

    // 회원 이름
    @Column(name = "name", length = 50)
    private String name;

    // 연락처
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    // 회원 권한 (기본값: USER)
    @Column(name = "role", length = 10, nullable = false)
    private String role = "USER";

    // Refresh Token의 해시값
    @Column(name = "refresh_token_hash", length = 100)
    private String refreshTokenHash;

    // JWT 무효화에 사용하는 토큰 버전
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    // 로그인 실패 횟수
    @Column(name = "failed_login_count", nullable = false)
    private Short failedLoginCount = 0;

    // 계정 잠금 해제 예정 시각
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    // 마지막 로그인 시각
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /* ==================== 사용자 환경 설정 ==================== */

    // 화면 테마 (기본값: SYSTEM)
    @Column(name = "theme", length = 6, nullable = false)
    private String theme = "SYSTEM";

    // 글자 크기 비율 (기본값: 100)
    @Column(name = "font_scale", nullable = false)
    private Short fontScale = 100;

    /* ==================== 기본 프로필 ==================== */

    // 생년월일
    @Column(name = "birth_date")
    private LocalDate birthDate;

    // 성별 코드
    @Column(name = "gender", length = 1, columnDefinition = "char(1)")
    private String gender;

    // 거주 지역 코드
    @Column(name = "region_code", length = 10)
    private String regionCode;

    // 시·도 이름
    @Column(name = "sido_name", length = 20)
    private String sidoName;

    // 시·군·구 이름
    @Column(name = "sigungu_name", length = 20)
    private String sigunguName;

    // 가구원 수
    @Column(name = "household_size")
    private Short householdSize;

    /* ==================== 소득 정보 ==================== */

    // 소득 입력 방식
    @Column(name = "income_input_type", length = 10)
    private String incomeInputType;

    // 소득 금액 데이터 (BLOB)
    @Lob
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "income_amount", columnDefinition = "blob")
    private byte[] incomeAmount;

    // 소득 기준 연도
    @Column(name = "income_base_year")
    private Short incomeBaseYear;

    // 기준 중위소득 대비 비율
    @Column(name = "median_income_pct", precision = 5, scale = 1)
    private BigDecimal medianIncomePct;

    /* ==================== 주거 및 가구 정보 ==================== */

    // 주거 형태
    @Column(name = "housing_type", length = 10)
    private String housingType;

    // 주택 소유 여부
    @Column(name = "has_house")
    private Boolean hasHouse;

    // 혼인 상태
    @Column(name = "marital_status", length = 10)
    private String maritalStatus;

    // 자녀 수
    @Column(name = "children_count")
    private Short childrenCount;

    // 가장 어린 자녀의 출생 연도
    @Column(name = "youngest_child_birth_year")
    private Short youngestChildBirthYear;

    // 복지 대상자 상태
    @Column(name = "welfare_status", length = 12)
    private String welfareStatus;

    /* ==================== 복수 선택 및 추가 정보 ==================== */

    // 가구 유형 코드 목록 (JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "household_type_codes", columnDefinition = "json")
    private String householdTypeCodes;

    // 사용자 상태 코드 목록 (JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "status_codes", columnDefinition = "json")
    private String statusCodes;

    // 민감정보 데이터 (BLOB)
    @Lob
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "sensitive_enc", columnDefinition = "blob")
    private byte[] sensitiveEnc;

    // 추가 질문 응답 (JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers", columnDefinition = "json")
    private String answers;

    // 제출 서류 관련 기록 (JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "doc_records", columnDefinition = "json")
    private String docRecords;

    /* ==================== 프로필 진행 상태 ==================== */

    // 프로필 데이터 버전
    @Column(name = "profile_version", nullable = false)
    private Integer profileVersion = 0;

    // 현재 프로필 작성 단계
    @Column(name = "profile_step", nullable = false)
    private Short profileStep = 0;

    // 프로필 작성 완료 시각
    @Column(name = "profile_completed_at")
    private LocalDateTime profileCompletedAt;

    /**
     * JPA에서 사용하는 기본 생성자.
     * 외부에서 직접 호출하지 못하도록 protected로 제한한다.
     */
    protected User() {
    }

    /**
     * 일반 이메일 회원가입용 생성자.
     */
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

    /**
     * 카카오 회원가입용 정적 팩토리 메서드.
     * 카카오 계정 식별자와 프로필 정보를 이용해 회원 객체를 생성한다.
     */
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

    /* ==================== Getter ==================== */

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

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public Integer getTokenVersion() {
        return tokenVersion;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getKakaoId() {
        return kakaoId;
    }

    /* ==================== 인증 정보 변경 ==================== */

    /**
     * 로그인 및 토큰 재발급 시 Refresh Token 해시를 갱신한다.
     */
    public void updateRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }

    /**
     * 토큰 버전을 증가시켜 기존 JWT를 무효화한다.
     */
    public void increaseTokenVersion() {
        this.tokenVersion++;
    }

    /**
     * 로그아웃 시 저장된 Refresh Token 해시를 제거한다.
     */
    public void clearRefreshTokenHash() {
        this.refreshTokenHash = null;
    }
}