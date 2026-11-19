package com.bokdeongi.backend.home.service;

import com.bokdeongi.backend.home.dto.response.HomeActivityResponse;
import com.bokdeongi.backend.home.dto.response.HomeActivityResponse.NewEligibleItem;
import com.bokdeongi.backend.home.repository.HomeActivityRepository;
import com.bokdeongi.backend.home.repository.HomeActivityRepository.EligibleActivityRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * HOME-02 신규 해당 배지 및 주간 요약 서비스.
 */
@Service
public class HomeActivityService {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

    private final HomeActivityRepository repository;

    public HomeActivityService(HomeActivityRepository repository) {
        this.repository = repository;
    }

    /**
     * 신규 해당 배지 및 주간 요약 조회.
     *
     * @param userId 로그인 사용자 ID
     * @return HOME-02 응답
     */
    public HomeActivityResponse getActivity(long userId) {

        LocalDate today = LocalDate.now(ZONE_ID);

        LocalDate weekStart = today.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );

        LocalDate weekEnd = weekStart.plusDays(6);

        LocalDateTime weekStartAt = weekStart.atStartOfDay();
        LocalDateTime nextWeekStartAt = weekEnd.plusDays(1).atStartOfDay();

        List<EligibleActivityRow> eligibleBenefits =
                repository.findEligibleActivities(userId);

        LocalDateTime lastLoginAt = repository.findLastLoginAt(userId);

        // 현재 DB 구조에서는 정확한 첫 로그인 여부를 판단할 수 없음.
        // 기존 응답 형식 유지를 위해 임시로 사용한다.
        boolean firstVisit = lastLoginAt == null;

        // 아직 확인하지 않은 적격 혜택
        List<NewEligibleItem> newEligibleBenefits = eligibleBenefits.stream()
                .filter(this::isNewEligible)
                .map(row -> new NewEligibleItem(
                        row.benefitId(),
                        row.title(),
                        true
                ))
                .toList();

        // 이번 주 신규 해당 혜택
        int newEligibleCount = (int) eligibleBenefits.stream()
                .filter(row -> isEligibleThisWeek(
                        row, weekStartAt, nextWeekStartAt
                ))
                .count();

        // 7일 이내 마감되는 혜택
        int closingSoonCount = (int) eligibleBenefits.stream()
                .filter(row -> isClosingSoon(row, today))
                .count();

        // 이번 주 변경된 공고
        int changedBenefitCount = (int) eligibleBenefits.stream()
                .filter(row -> isChangedThisWeek(
                        row, weekStartAt, nextWeekStartAt
                ))
                .count();

        // 요약할 내용이 없으면 카드 숨김
        boolean visible = newEligibleCount > 0
                || closingSoonCount > 0
                || changedBenefitCount > 0;

        HomeActivityResponse.WeeklySummary weeklySummary =
                new HomeActivityResponse.WeeklySummary(
                        visible,
                        newEligibleCount,
                        closingSoonCount,
                        changedBenefitCount,
                        weekStart,
                        weekEnd
                );

        return new HomeActivityResponse(
                firstVisit,
                newEligibleBenefits,
                weeklySummary
        );
    }

    /**
     * 아직 확인하지 않은 적격 혜택인지 판단한다.
     *
     * @param row 적격 혜택 조회 결과
     * @return 미확인 적격 혜택 여부
     */
    private boolean isNewEligible(EligibleActivityRow row) {
        return row.firstEligibleAt() != null
                && row.seenAt() == null;
    }

    /**
     * 이번 주 신규 해당 혜택 여부를 판단한다.
     *
     * @param row 적격 혜택 조회 결과
     * @param start 이번 주 시작 시각
     * @param end 다음 주 시작 시각
     * @return 이번 주 최초 적격 여부
     */
    private boolean isEligibleThisWeek(
            EligibleActivityRow row,
            LocalDateTime start,
            LocalDateTime end
    ) {
        LocalDateTime firstEligibleAt = row.firstEligibleAt();

        return firstEligibleAt != null
                && !firstEligibleAt.isBefore(start)
                && firstEligibleAt.isBefore(end);
    }

    /**
     * 오늘부터 7일 이내 신청 마감 여부를 판단한다.
     *
     * @param row 적격 혜택 조회 결과
     * @param today 현재 날짜
     * @return 마감 임박 여부
     */
    private boolean isClosingSoon(
            EligibleActivityRow row,
            LocalDate today
    ) {
        LocalDate applyEnd = row.applyEnd();

        return applyEnd != null
                && !applyEnd.isBefore(today)
                && !applyEnd.isAfter(today.plusDays(7));
    }

    /**
     * 이번 주 변경된 공고 여부를 판단한다.
     *
     * @param row 적격 혜택 조회 결과
     * @param start 이번 주 시작 시각
     * @param end 다음 주 시작 시각
     * @return 이번 주 공고 변경 여부
     */
    private boolean isChangedThisWeek(
            EligibleActivityRow row,
            LocalDateTime start,
            LocalDateTime end
    ) {
        LocalDateTime changedAt = row.lastChangedAt();

        return changedAt != null
                && !changedAt.isBefore(start)
                && changedAt.isBefore(end);
    }

    /**
     * 사용자가 혜택을 확인한 것으로 처리한다.
     *
     * @param userId 로그인 사용자 ID
     * @param benefitId 확인한 혜택 ID
     * @return 이번 요청에서 확인 처리되었는지 여부
     */
    @Transactional
    public boolean markBenefitAsSeen(long userId, long benefitId) {
        return repository.markBenefitAsSeen(userId, benefitId) > 0;
    }
}