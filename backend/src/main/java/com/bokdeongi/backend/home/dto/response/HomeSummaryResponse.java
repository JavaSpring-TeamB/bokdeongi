package com.bokdeongi.backend.home.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.time.OffsetDateTime;

/**
 * HOME-01 홈 대시보드 응답 DTO
 *
 * 로그인 사용자의 프로필 완료 여부, 예상 수령 금액,
 * 마감 임박 혜택, 신청현황, 맞춤 추천 및 아깝게 탈락한 혜택 정보를
 * 홈 화면에 전달하기 위한 응답 데이터 구조이다.
 */
public record HomeSummaryResponse(
        boolean profileCompleted,
        Short profileStep,
        boolean eligibilityUpdating,
        AmountSummary amountSummary,
        List<ClosingSoonItem> closingSoon,
        ApplicationSummary applicationSummary,
        List<RecommendationItem> recommendations,
        List<RecommendationItem> nearMissBenefits,
        long totalCount,
        long eligibleCount,
        long conditionalCount,
        long ineligibleCount,
        OffsetDateTime benefitDataUpdatedAt
) {

    /**
     * HOME-01 수령 가능 금액 요약 DTO
     *
     * 예상·최대 수령액과 금액 미확인 건수를 제공한다.
     * annualizedAmount는 중복 수급 제한을 적용하지 않은
     * 연간 환산 참고 금액이며 실제 예상 수령액은 아니다.
     */
    public record AmountSummary(
            Long estimatedAmount,
            Long maxAmount,
            Long annualizedAmount,
            int unknownAmountCount,
            boolean duplicateExclusionApplied
    ) {
    }

    public record ClosingSoonItem(
            Long benefitId,
            String title,
            LocalDate applyEnd,
            long dDay,
            String eligibilityStatus
    ) {
    }

    public record ApplicationSummary(
            int inProgressCount,
            int completedCount,
            List<ApplicationItem> items
    ) {
    }

    public record ApplicationItem(
            Long benefitId,
            String title,
            String appStatus,
            Short step,
            Integer progressPercent
    ) {
    }

    public record RecommendationItem(
            Long benefitId,
            String title,
            String eligibilityStatus,
            String reason
    ) {
    }
}