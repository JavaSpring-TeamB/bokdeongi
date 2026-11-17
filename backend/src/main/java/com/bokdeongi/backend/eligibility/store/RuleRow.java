package com.bokdeongi.backend.eligibility.store;

import java.math.BigDecimal;

/** benefit_rules 한 줄 그대로. value는 JSON 배열 원문 (예: [19, 34], ["JA0317"]). */
public record RuleRow(long id, long benefitId, int group, String field, String operator, String valueJson,
        String source, String sourceQuote, BigDecimal confidence) {
}
