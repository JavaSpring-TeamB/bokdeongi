package com.bokdeongi.backend.home.service;

import com.bokdeongi.backend.benefit.recommend.RecommendResponse;
import com.bokdeongi.backend.benefit.recommend.RecommendService;
import com.bokdeongi.backend.home.dto.response.HomeSummaryResponse;
import com.bokdeongi.backend.home.repository.HomeQueryRepository;
import com.bokdeongi.backend.home.repository.HomeQueryRepository.ApplicationRow;
import com.bokdeongi.backend.home.repository.HomeQueryRepository.EligibleBenefitRow;
import com.bokdeongi.backend.profile.dto.response.ProfileResponse;
import com.bokdeongi.backend.profile.service.ProfileService;
import org.springframework.stereotype.Service;
import com.bokdeongi.backend.home.repository.HomeQueryRepository.EligibilityCountRow;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * HOME-01 홈 대시보드 비즈니스 로직 Service
 *
 * 로그인 사용자의 프로필, 자격 판정 결과 및 신청현황을 조회하고
 * 홈 화면에 필요한 요약 데이터로 가공한다.
 *
 * 기존 프로필 및 추천 서비스를 재사용하며
 * 자격 판정 로직은 중복 구현하지 않는다.
 */
@Service
public class HomeService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    // 임시 정책: 마감 7일 이내를 마감 임박으로 표시
    private static final int CLOSING_SOON_DAYS = 7;
    private static final int MAX_HOME_ITEMS = 5;

    private static final Set<String> COMPLETED_STATUSES =
            Set.of("SELECTED", "REJECTED", "CANCELED");

    private final HomeQueryRepository homeQueryRepository;
    private final ProfileService profileService;
    private final RecommendService recommendService;

    public HomeService(
            HomeQueryRepository homeQueryRepository,
            ProfileService profileService,
            RecommendService recommendService
    ) {
        this.homeQueryRepository = homeQueryRepository;
        this.profileService = profileService;
        this.recommendService = recommendService;
    }

    /**
     * 로그인 사용자의 홈 대시보드 요약 정보를 조회한다.
     *
     * 프로필이 미완성인 경우 자격 판정 결과를 조회하지 않는다.
     * 자격 판정 결과가 갱신 중이면 오래된 추천 결과를 표시하지 않는다.
     */
    public HomeSummaryResponse getSummary(long userId) {

        ProfileResponse profile = profileService.getProfile(userId);
        boolean completed = profile.profileCompleted();

        List<EligibleBenefitRow> eligibleBenefits = List.of();
        List<HomeSummaryResponse.RecommendationItem> recommendations = List.of();
        List<HomeSummaryResponse.RecommendationItem> nearMissBenefits = List.of();

        boolean eligibilityUpdating = false;

        // 맞춤 혜택 판정 통계 기본값
        EligibilityCountRow eligibilityCounts =
                new EligibilityCountRow(0L, 0L, 0L, 0L);

        // 공고 데이터 마지막 정상 동기화 시각 (UTC)
        LocalDateTime updatedAt =
                homeQueryRepository.findBenefitDataUpdatedAt();

        OffsetDateTime benefitDataUpdatedAt =
                updatedAt == null
                        ? null
                        : updatedAt.atOffset(ZoneOffset.UTC);

        if (completed) {

            // 기존 추천 서비스를 통해 해당 혜택과 판정 갱신 상태를 조회한다.
            RecommendResponse eligibleResponse =
                    recommendService.recommended(
                            userId,
                            "ELIGIBLE",
                            null,
                            null,
                            "RECOMMENDED",
                            0,
                            MAX_HOME_ITEMS
                    );

            eligibilityUpdating = eligibleResponse.updating();

            // 판정 결과가 최신인 경우에만
            // 수령 가능 금액, 마감 임박 혜택, 통계 및 추천 정보를 조회한다.
            if (!eligibilityUpdating) {

                // 현재 프로필 버전에 해당하는 적격 혜택 조회
                eligibleBenefits = homeQueryRepository.findEligibleBenefits(userId);

                // 현재 프로필 기준 맞춤 혜택 판정 통계 조회
                eligibilityCounts = homeQueryRepository.findEligibilityCounts(userId);

                recommendations = eligibleResponse.items().stream()
                        .map(item -> new HomeSummaryResponse.RecommendationItem(
                                item.benefitId(),
                                item.title(),
                                item.status(),
                                item.reason()
                        ))
                        .toList();

                // 해당 혜택이 없는 경우 아깝게 탈락한 혜택을 조회한다.
                if (eligibleResponse.totalElements() == 0) {

                    RecommendResponse nearMissResponse =
                            recommendService.recommended(
                                    userId,
                                    "NEAR_MISS",
                                    null,
                                    null,
                                    "RECOMMENDED",
                                    0,
                                    MAX_HOME_ITEMS
                            );

                    // 아깝게 탈락한 혜택도 최신 판정 결과만 표시한다.
                    if (!nearMissResponse.updating()) {
                        nearMissBenefits = nearMissResponse.items().stream()
                                .map(item -> new HomeSummaryResponse.RecommendationItem(
                                        item.benefitId(),
                                        item.title(),
                                        item.status(),
                                        item.reason()
                                ))
                                .toList();
                    }
                }
            }
        }

        // 신청현황은 자격 판정 갱신 여부와 관계없이 조회한다.
        List<ApplicationRow> applications =
                homeQueryRepository.findApplications(userId);

        return new HomeSummaryResponse(
                completed,
                profile.profileStep(),
                eligibilityUpdating,
                createAmountSummary(eligibleBenefits),
                createClosingSoon(eligibleBenefits),
                createApplicationSummary(applications),
                recommendations,
                nearMissBenefits,
                eligibilityCounts.totalCount(),
                eligibilityCounts.eligibleCount(),
                eligibilityCounts.conditionalCount(),
                eligibilityCounts.ineligibleCount(),
                benefitDataUpdatedAt
        );
    }

    /**
     * HOME-01 수령 가능 금액 요약 계산
     *
     * 해당(ELIGIBLE) 혜택의 annualAmount를 합산한다.
     * 연간 환산 금액은 중복 제외 전 참고 금액으로 제공하며,
     * 중복 불가 규칙 및 예상 수령액 계산 기준이 확정되기 전까지
     * estimatedAmount와 maxAmount는 null로 반환한다.
     */
    private HomeSummaryResponse.AmountSummary createAmountSummary(
            List<EligibleBenefitRow> benefits
    ) {
        int unknownCount = 0;
        long annualizedAmount = 0L;
        boolean hasAnnualAmount = false;

        for (EligibleBenefitRow benefit : benefits) {

            Long annualAmount = benefit.annualAmount();

            // 연간 환산 금액이 없는 혜택은 합계에서 제외한다.
            if (annualAmount == null) {
                unknownCount++;
                continue;
            }

            // 음수 금액은 유효한 지원 금액으로 취급하지 않는다.
            if (annualAmount < 0) {
                unknownCount++;
                continue;
            }

            // long 범위를 초과하는 합계는 계산 오류로 처리한다.
            annualizedAmount = Math.addExact(
                    annualizedAmount,
                    annualAmount
            );

            hasAnnualAmount = true;
        }

        return new HomeSummaryResponse.AmountSummary(
                null,
                null,
                hasAnnualAmount ? annualizedAmount : null,
                unknownCount,
                false
        );
    }

    /**
     * 마감일이 가까운 해당 혜택을 조회한다.
     */
    private List<HomeSummaryResponse.ClosingSoonItem> createClosingSoon(
            List<EligibleBenefitRow> benefits
    ) {
        LocalDate today = LocalDate.now(KOREA);

        return benefits.stream()
                .filter(b -> b.applyEnd() != null)
                .filter(b -> !b.applyEnd().isBefore(today))
                .filter(b -> !b.applyEnd().isAfter(
                        today.plusDays(CLOSING_SOON_DAYS)))
                .sorted((a, b) -> a.applyEnd().compareTo(b.applyEnd()))
                .limit(MAX_HOME_ITEMS)
                .map(b -> new HomeSummaryResponse.ClosingSoonItem(
                        b.benefitId(),
                        b.title(),
                        b.applyEnd(),
                        ChronoUnit.DAYS.between(today, b.applyEnd()),
                        "ELIGIBLE"
                ))
                .toList();
    }

    /**
     * 신청 상태별 건수와 진행 단계를 홈 요약 카드로 변환한다.
     */
    private HomeSummaryResponse.ApplicationSummary createApplicationSummary(
            List<ApplicationRow> applications
    ) {
        int inProgressCount = (int) applications.stream()
                .filter(a -> "IN_PROGRESS".equals(a.appStatus())
                        || "SUBMITTED".equals(a.appStatus()))
                .count();

        int completedCount = (int) applications.stream()
                .filter(a -> COMPLETED_STATUSES.contains(a.appStatus()))
                .count();

        List<HomeSummaryResponse.ApplicationItem> items =
                applications.stream()
                        .limit(MAX_HOME_ITEMS)
                        .map(a -> new HomeSummaryResponse.ApplicationItem(
                                a.benefitId(),
                                a.title(),
                                a.appStatus(),
                                a.step(),
                                calculateProgress(a.step())
                        ))
                        .toList();

        return new HomeSummaryResponse.ApplicationSummary(
                inProgressCount,
                completedCount,
                items
        );
    }

    /**
     * 신청 도우미의 4단계를 표시용 진행률로 변환한다.
     */
    private Integer calculateProgress(Short step) {
        if (step == null || step < 1 || step > 4) {
            return null;
        }
        return step * 25;
    }
}