package com.bokdeongi.backend.eligibility.store;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * users에서 판정에 쓰는 칸 그대로. JSON 칸은 원문 문자열.
 *
 * <p>민감 항목(sensitive_enc)은 아직 넣지 않는다. 복호화(D-43, A 진행 중)가 들어오면 sensitiveCodesJson을 추가한다.
 * 그 전까지 민감 조건은 모두 '조건부'로 판정된다.
 */
public record ProfileRow(long userId, LocalDate birthDate, String gender, String sidoName, String sigunguName,
        BigDecimal medianIncomePct, Integer householdSize, String householdTypeCodesJson, String statusCodesJson,
        String housingType, Boolean hasHouse, String maritalStatus, Integer childrenCount,
        Integer youngestChildBirthYear, String welfareStatus, String answersJson, int profileVersion) {
}
