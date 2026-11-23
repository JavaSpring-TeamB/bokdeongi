package com.bokdeongi.backend.benefit.dto.response;

import com.bokdeongi.backend.benefit.entity.Benefit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record BenefitDetailResponse(
        Long id,
        String title,
        String status,
        boolean ended,
        String summaryWho,
        String summaryWhat,
        String summaryHow,
        String summaryWhen,
        Integer maxAmount,
        String amountPeriod,
        String targetText,
        LocalDate applyStart,
        LocalDate applyEnd,
        String applyPeriodText,
        String contentText,
        String criteriaText,
        String applyMethodText,
        String requiredDocsText,
        String hashtags,
        String detailUrl,
        String onlineApplyUrl,
        LocalDateTime syncedAt,
        List<String> missingFields
) {
    public static BenefitDetailResponse from(Benefit benefit) {
        String status = benefit.getStatus();

        List<String> missing = new ArrayList<>();

        addIfBlank(missing, "contentText", benefit.getContentText());
        addIfBlank(missing, "targetText", benefit.getTargetText());
        addIfBlank(missing, "applyMethodText", benefit.getApplyMethodText());
        addIfBlank(missing, "requiredDocsText", benefit.getRequiredDocsText());
        addIfBlank(missing, "applyPeriodText", benefit.getApplyPeriodText());

        if (benefit.getMaxAmount() == null) {
            missing.add("maxAmount");
        }

        return new BenefitDetailResponse(
                benefit.getId(),
                benefit.getTitle(),
                status,
                Benefit.STATUS_ENDED.equals(status)
                        || Benefit.STATUS_EXPIRED.equals(status),
                benefit.getSummaryWho(),
                benefit.getSummaryWhat(),
                benefit.getSummaryHow(),
                benefit.getSummaryWhen(),
                benefit.getMaxAmount(),
                benefit.getAmountPeriod(),
                benefit.getTargetText(),
                benefit.getApplyStart(),
                benefit.getApplyEnd(),
                benefit.getApplyPeriodText(),
                benefit.getContentText(),
                benefit.getCriteriaText(),
                benefit.getApplyMethodText(),
                benefit.getRequiredDocsText(),
                benefit.getHashtags(),
                benefit.getDetailUrl(),
                benefit.getOnlineApplyUrl(),
                benefit.getSyncedAt(),
                List.copyOf(missing)
        );
    }

    private static void addIfBlank(
            List<String> missing,
            String fieldName,
            String value
    ) {
        if (value == null || value.isBlank()) {
            missing.add(fieldName);
        }
    }
}