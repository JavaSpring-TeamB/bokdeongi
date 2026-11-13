package com.bokdeongi.eligibility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * 판정에 쓰는 회원 정보 (users에서 꺼내 복호화까지 끝낸 값).
 * 판정 함수는 DB·엔티티를 모르고 이 값만 본다 (D-55).
 *
 * <p>미입력 규칙 (D-45): null = 건너뜀(그 조건은 조건부), 빈 Set = 해당 없음.
 *
 * @param sensitiveCodes 민감 JA 코드. 민감정보 동의가 없거나 건너뛰었으면 null
 * @param statusCodes    현재 상태 JA 코드. 자영업은 {@link #SELF_EMPLOYED}
 * @param answers        공고별 추가 질문 답 {question_key: value}
 */
public record ProfileSnapshot(
        LocalDate birthDate,
        String gender,
        String sidoName,
        String sigunguName,
        BigDecimal medianIncomePct,
        Integer householdSize,
        Set<String> householdTypeCodes,
        Set<String> statusCodes,
        Set<String> sensitiveCodes,
        String housingType,
        Boolean hasHouse,
        String maritalStatus,
        Integer childrenCount,
        Integer youngestChildBirthYear,
        String welfareStatus,
        Map<String, String> answers) {

    /** 자영업·소상공인. JA 코드가 없어 별도 값으로 저장한다 (D-49). */
    public static final String SELF_EMPLOYED = "SELF_EMPLOYED";

    public ProfileSnapshot {
        householdTypeCodes = householdTypeCodes == null ? null : Set.copyOf(householdTypeCodes);
        statusCodes = statusCodes == null ? null : Set.copyOf(statusCodes);
        sensitiveCodes = sensitiveCodes == null ? null : Set.copyOf(sensitiveCodes);
        answers = answers == null ? Map.of() : Map.copyOf(answers);
    }

    /** 모든 항목을 건너뛴 회원. 테스트에서 필요한 값만 바꿔 쓴다. */
    public static ProfileSnapshot empty() {
        return new ProfileSnapshot(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, Map.of());
    }

    public ProfileSnapshot withBirthDate(LocalDate value) {
        return new ProfileSnapshot(value, gender, sidoName, sigunguName, medianIncomePct, householdSize,
                householdTypeCodes, statusCodes, sensitiveCodes, housingType, hasHouse, maritalStatus,
                childrenCount, youngestChildBirthYear, welfareStatus, answers);
    }

    public ProfileSnapshot withGender(String value) {
        return new ProfileSnapshot(birthDate, value, sidoName, sigunguName, medianIncomePct, householdSize,
                householdTypeCodes, statusCodes, sensitiveCodes, housingType, hasHouse, maritalStatus,
                childrenCount, youngestChildBirthYear, welfareStatus, answers);
    }

    public ProfileSnapshot withRegion(String sido, String sigungu) {
        return new ProfileSnapshot(birthDate, gender, sido, sigungu, medianIncomePct, householdSize,
                householdTypeCodes, statusCodes, sensitiveCodes, housingType, hasHouse, maritalStatus,
                childrenCount, youngestChildBirthYear, welfareStatus, answers);
    }

    public ProfileSnapshot withIncomePct(BigDecimal value) {
        return new ProfileSnapshot(birthDate, gender, sidoName, sigunguName, value, householdSize,
                householdTypeCodes, statusCodes, sensitiveCodes, housingType, hasHouse, maritalStatus,
                childrenCount, youngestChildBirthYear, welfareStatus, answers);
    }

    public ProfileSnapshot withHousehold(Integer size, Set<String> typeCodes, Boolean hasHouseValue) {
        return new ProfileSnapshot(birthDate, gender, sidoName, sigunguName, medianIncomePct, size,
                typeCodes, statusCodes, sensitiveCodes, housingType, hasHouseValue, maritalStatus,
                childrenCount, youngestChildBirthYear, welfareStatus, answers);
    }

    public ProfileSnapshot withStatus(Set<String> status, Set<String> sensitive) {
        return new ProfileSnapshot(birthDate, gender, sidoName, sigunguName, medianIncomePct, householdSize,
                householdTypeCodes, status, sensitive, housingType, hasHouse, maritalStatus,
                childrenCount, youngestChildBirthYear, welfareStatus, answers);
    }

    public ProfileSnapshot withFamily(String marital, Integer children, Integer youngestBirthYear) {
        return new ProfileSnapshot(birthDate, gender, sidoName, sigunguName, medianIncomePct, householdSize,
                householdTypeCodes, statusCodes, sensitiveCodes, housingType, hasHouse, marital,
                children, youngestBirthYear, welfareStatus, answers);
    }

    public ProfileSnapshot withHousing(String housing, String welfare) {
        return new ProfileSnapshot(birthDate, gender, sidoName, sigunguName, medianIncomePct, householdSize,
                householdTypeCodes, statusCodes, sensitiveCodes, housing, hasHouse, maritalStatus,
                childrenCount, youngestChildBirthYear, welfare, answers);
    }

    public ProfileSnapshot withAnswers(Map<String, String> value) {
        return new ProfileSnapshot(birthDate, gender, sidoName, sigunguName, medianIncomePct, householdSize,
                householdTypeCodes, statusCodes, sensitiveCodes, housingType, hasHouse, maritalStatus,
                childrenCount, youngestChildBirthYear, welfareStatus, value);
    }
}
