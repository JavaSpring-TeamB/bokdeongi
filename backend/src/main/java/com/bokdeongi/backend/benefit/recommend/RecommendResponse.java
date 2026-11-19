package com.bokdeongi.backend.benefit.recommend;

import com.bokdeongi.backend.benefit.recommend.RecommendView.CheckType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * GET /api/benefits/recommended 응답 (BEN-01).
 *
 * @param counts   탭별 건수 (필터 적용)
 * @param updating 프로필이 바뀌어 재판정 중이면 true. 그동안은 이전 결과를 보여 준다 (BEN-01 예외)
 * @param filters  이 탭에서 고를 수 있는 지역·분야
 */
public record RecommendResponse(String tab, String sort, int page, int size, long totalElements, int totalPages,
        Map<String, Long> counts, boolean updating, Filters filters, List<Item> items) {

    public record Filters(List<String> regions, List<String> fields) {
    }

    /**
     * 카드 한 장.
     *
     * @param status           ELIGIBLE / CONDITIONAL / INELIGIBLE
     * @param recommended      해당 탭 추천순 상위 3건 '추천' 표시
     * @param newlyEligible    직전 판정은 해당이 아니었는데 이번에 해당 (HOME-02 신규 배지)
     * @param reason           해당 탭: 추천 이유 ("나이·거주지 조건을 충족해요")
     * @param checks           조건부 탭: 확인할 조건 (QUESTION이면 예/아니오/아직 몰라요로 바로 답할 수 있음)
     * @param nearMiss         아깝게 탈락 탭: 충족 시점과 미충족 조건
     * @param conditionResults 조건별 판정 근거 (eligibility_results.trace 그대로)
     */
    public record Item(long benefitId, String title, String category, String summary, String supportType,
            String orgName, String region, String status, boolean recommended, boolean newlyEligible, Amount amount,
            Period period, ApplyBadge apply, String reason, List<Check> checks, NearMissInfo nearMiss,
            List<String> hashtags, String sourceUrl, boolean bookmarked, List<Map<String, Object>> conditionResults) {
    }

    /** @param text "월 최대 20만원" */
    public record Amount(long max, String period, Long annual, String text) {
    }

    /** @param text "~ 2026.11.30 (D-12)", "상시 신청", "공고 확인 필요" */
    public record Period(LocalDate start, LocalDate end, String status, String precision, boolean recurringYearly,
            Integer dDay, String text) {
    }

    /** 신청 난이도 배지 (BEN-03). method: "온라인 신청" / "방문 신청" / null(정보 없음) */
    public record ApplyBadge(boolean online, String method, Integer selfDocCount, String docText) {
    }

    /** @param questionKey QUESTION일 때 PUT /api/me/answers에 보낼 키 */
    public record Check(CheckType type, String field, String questionKey, String text, String sourceQuote) {
    }

    /** @param reason "2027년 3월 2일부터 나이 조건을 충족해요" */
    public record NearMissInfo(String reason, String field, String label, String expected, String actual,
            String sourceQuote) {
    }
}
