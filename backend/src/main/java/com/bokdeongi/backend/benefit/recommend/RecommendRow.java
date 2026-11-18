package com.bokdeongi.backend.benefit.recommend;

import java.time.LocalDate;

/** 맞춤 목록 SQL 한 줄 (benefits + eligibility_results + user_benefits). JSON 칸은 원문 문자열. */
public record RecommendRow(long benefitId, String title, String serviceField, String supportType, String summary,
        Long maxAmount, String amountPeriod, Long annualAmount, LocalDate applyStart, LocalDate applyEnd,
        String dateStatus, String datePrecision, boolean recurringYearly, Boolean onlineApply, Integer selfDocCount,
        String hashtagsJson, String detailUrl, String onlineApplyUrl, String orgName, String regionName,
        String result, String previousResult, boolean nearMiss, String nearMissReason, String missingFieldsJson,
        String traceJson, boolean bookmarked) {
}
