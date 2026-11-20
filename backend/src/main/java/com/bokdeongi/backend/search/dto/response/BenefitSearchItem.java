package com.bokdeongi.backend.search.dto.response;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.search.service.BenefitSearchPeriodService.ExpectedPeriod;

import java.time.LocalDate;

public record BenefitSearchItem(
        Long id,
        String title,
        String summaryWho,
        String summaryWhat,
        String summaryHow,
        String summaryWhen,
        String orgName,
        String hashtags,
        String eligibilityResult,
        LocalDate applyStart,
        LocalDate applyEnd,
        Boolean recurringYearly,
        LocalDate expectedApplyStart,
        LocalDate expectedApplyEnd
) {

    public static BenefitSearchItem from(Benefit benefit) {
        return from(benefit, null, null);
    }

    public static BenefitSearchItem from(
            Benefit benefit,
            String eligibilityResult
    ) {
        return from(benefit, eligibilityResult, null);
    }

    public static BenefitSearchItem from(
            Benefit benefit,
            String eligibilityResult,
            ExpectedPeriod expectedPeriod
    ) {
        return new BenefitSearchItem(
                benefit.getId(),
                benefit.getTitle(),
                benefit.getSummaryWho(),
                benefit.getSummaryWhat(),
                benefit.getSummaryHow(),
                benefit.getSummaryWhen(),
                benefit.getOrgName(),
                benefit.getHashtags(),
                eligibilityResult,
                benefit.getApplyStart(),
                benefit.getApplyEnd(),
                benefit.getRecurringYearly(),
                expectedPeriod == null
                        ? null
                        : expectedPeriod.expectedApplyStart(),
                expectedPeriod == null
                        ? null
                        : expectedPeriod.expectedApplyEnd()
        );
    }
}