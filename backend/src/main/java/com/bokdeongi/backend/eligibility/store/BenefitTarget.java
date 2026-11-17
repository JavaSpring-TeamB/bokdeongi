package com.bokdeongi.backend.eligibility.store;

import java.time.LocalDate;

/**
 * 판정 대상 혜택 한 건에서 판정에 필요한 칸만 (benefits).
 *
 * @param regionCode    소관기관 지역 코드 (regions.region_code, D-56). 전국이면 null
 * @param targetScope   PERSONAL / SMALL_BIZ / CORP (D-49, D-87)
 * @param rulesStatus   PARSED / PARTIAL / FAILED 등. FAILED면 규칙이 빠졌을 수 있다
 * @param unparsedJson  규칙으로 못 바꾼 조건 [{sourceQuote, ruleGroup}] (D-64)
 * @param applyEnd      신청 마감일 (아깝게 탈락 계산용)
 * @param dateStatus    PARSED / ALWAYS / ... (ALWAYS = 상시 신청)
 */
public record BenefitTarget(long id, String regionCode, String targetScope, String rulesStatus, String unparsedJson,
        LocalDate applyEnd, String dateStatus) {
}
