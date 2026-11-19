package com.bokdeongi.backend.home.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * HOME-02 신규 해당 배지 및 주간 요약 응답 DTO.
 *
 * @param firstVisit 첫 방문 여부
 * @param newEligibleBenefits 신규 해당 혜택 목록
 * @param weeklySummary 주간 요약 정보
 */
public record HomeActivityResponse(
        boolean firstVisit,
        List<NewEligibleItem> newEligibleBenefits,
        WeeklySummary weeklySummary
) {

    /**
     * 신규 해당 혜택 정보.
     */
    public record NewEligibleItem(
            Long benefitId,
            String title,
            boolean newBadge
    ) {
    }

    /**
     * 주간 요약 카드 정보.
     */
    public record WeeklySummary(
            boolean visible,
            int newEligibleCount,
            int closingSoonCount,
            int changedBenefitCount,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
    }
}