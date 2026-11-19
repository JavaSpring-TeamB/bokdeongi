package com.bokdeongi.backend.benefit.favorite;

import com.bokdeongi.backend.benefit.recommend.RecommendRow;
import java.time.LocalDateTime;

/**
 * 관심·신청 중 혜택 한 줄 (user_benefits + benefits + eligibility_results). 시각은 UTC.
 *
 * @param card           카드 문구를 만들 값 (맞춤 목록과 같은 모양, 판정이 없으면 result = null)
 * @param userBenefitId  user_benefits.id (신청 건 id)
 * @param benefitStatus  ACTIVE / ENDED / EXPIRED
 */
public record FavoriteRow(RecommendRow card, long userBenefitId, boolean bookmarked, LocalDateTime bookmarkedAt,
                          String appStatus, String benefitStatus, String exclusionsJson, String lastChangeSummary,
                          LocalDateTime lastChangedAt) {
}
