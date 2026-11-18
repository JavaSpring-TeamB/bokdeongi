package com.bokdeongi.backend.eligibility.store;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * users에서 판정에 쓰는 칸 그대로. JSON 칸은 원문 문자열.
 *
 * <p>sensitiveCodesJson은 sensitive_enc를 복호화한 JSON 배열(예: ["JA0328"]). 민감정보 미동의·미입력이면 null이고,
 * 그러면 민감 조건은 '조건부'가 된다 (D-43, PROF-04).
 */
public record ProfileRow(long userId, LocalDate birthDate, String gender, String sidoName, String sigunguName,
        BigDecimal medianIncomePct, Integer householdSize, String householdTypeCodesJson, String statusCodesJson,
        String housingType, Boolean hasHouse, String maritalStatus, Integer childrenCount,
        Integer youngestChildBirthYear, String welfareStatus, String answersJson, String sensitiveCodesJson,
        int profileVersion) {
}
