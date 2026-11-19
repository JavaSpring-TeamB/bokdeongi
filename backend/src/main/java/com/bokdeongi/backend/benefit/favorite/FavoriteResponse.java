package com.bokdeongi.backend.benefit.favorite;

import com.bokdeongi.backend.benefit.recommend.RecommendResponse.Amount;
import com.bokdeongi.backend.benefit.recommend.RecommendResponse.Period;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/me/favorites 응답 (BEN-12).
 *
 * @param sort   DEADLINE(마감순, 마감일 없는 혜택은 맨 뒤) / SAVED(저장순)
 * @param items  신청할 수 있는 관심 혜택
 * @param ended  종료(ENDED)·기간 만료(EXPIRED) 관심 혜택. 화면 하단 '종료' 묶음
 */
public record FavoriteResponse(String sort, int count, List<Item> items, List<Item> ended) {

    /**
     * 카드 한 장.
     *
     * @param status        판정 ELIGIBLE / CONDITIONAL / INELIGIBLE. 아직 판정 전이면 null
     * @param applying      신청 진행 중(IN_PROGRESS·SUBMITTED)이면 '신청 중' 배지
     * @param applicationId 신청 건 id (user_benefits.id). 신청현황 링크에 쓴다
     * @param lastChange    관심 저장 뒤 공고가 바뀌었으면 변경 요약 (DATA-08)
     * @param warnings      함께 관심·신청 중인 혜택과 중복 지원이 안 될 수 있으면 (BEN-09)
     */
    public record Item(long benefitId, String title, String category, String orgName, String region, String status,
                       Amount amount, Period period, List<String> hashtags, boolean applying, Long applicationId,
                       String appStatus, OffsetDateTime bookmarkedAt, LastChange lastChange, List<Warning> warnings) {
    }

    /** @param summary "신청기한이 변경됐어요 (외 1건)" */
    public record LastChange(String summary, OffsetDateTime changedAt) {
    }

    /**
     * @param level STRONG "함께 받을 수 없어요" / CHECK "중복 여부 확인 필요"(약하게)
     * @param text  화면 문구
     */
    public record Warning(long otherBenefitId, String otherTitle, String level, String text, String sourceQuote) {
    }
}
