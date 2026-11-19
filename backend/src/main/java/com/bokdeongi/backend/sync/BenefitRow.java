package com.bokdeongi.backend.sync;

import com.bokdeongi.backend.eligibility.Rule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API 원문을 가공해 benefits 한 줄에 쓸 값 (DATA-01). 칸 이름·크기는 공용 DB 스키마(v2.10)와 같다.
 * AI 요약·쉬운 말·변경 감지 칸은 여기서 쓰지 않는다 (ai_status만 PENDING으로 돌린다).
 *
 * @param status    ACTIVE / EXPIRED (신청 기간이 지남, D-85). ENDED는 API에서 사라진 경우라 계획 단계에서 정한다
 * @param codeRules 지원조건 코드로 만든 규칙 (benefit_rules, source = CODE)
 */
public record BenefitRow(
        String serviceId,
        String title,
        String purposeText,
        String supportType,
        String targetText,
        String criteriaText,
        String contentText,
        String applyMethodText,
        String applyPeriodText,
        String requiredDocsText,
        String officialDocsText,
        String selfDocsText,
        String orgName,
        String deptName,
        String receivingOrg,
        String contact,
        String onlineApplyUrl,
        String detailUrl,
        String serviceField,
        String userType,
        Integer viewCount,
        LocalDateTime apiModifiedAt,
        String status,
        Integer ageMin,
        Integer ageMax,
        String yCodesJson,
        String conditionsRawJson,
        String targetScope,
        String regionCode,
        LocalDate applyStart,
        LocalDate applyEnd,
        String dateStatus,
        String datePrecision,
        String dateSourceQuote,
        boolean recurringYearly,
        Integer maxAmount,
        String amountPeriod,
        Integer annualAmount,
        BigDecimal amountConfidence,
        String amountSourceQuote,
        String exclusionsJson,
        String documentsJson,
        Boolean onlineApply,
        Integer selfDocCount,
        String hashtagsJson,
        String hashtagCandidatesJson,
        List<Rule> codeRules,
        List<String> warnings) {

    public BenefitRow {
        codeRules = List.copyOf(codeRules);
        warnings = List.copyOf(warnings);
    }
}
