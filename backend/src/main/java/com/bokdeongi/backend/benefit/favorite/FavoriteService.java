package com.bokdeongi.backend.benefit.favorite;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관심 혜택 목록 (BEN-12)과 중복 수혜 경고 (BEN-09).
 *
 * <p>관심 등록·해제(BEN-07, POST·DELETE /api/me/favorites/{benefitId})는 A 담당이라 여기서는 읽기만 한다.
 * 혜택 상세·신청 시작 화면은 {@link #warningsFor}로 같은 경고를 붙일 수 있다.
 */
@Service
@Transactional(readOnly = true)
public class FavoriteService {

    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final FavoriteQuery query;
    private final Clock clock = Clock.systemUTC();

    public FavoriteService(FavoriteQuery query) {
        this.query = query;
    }

    public FavoriteResponse favorites(long userId, String sort) {
        FavoriteView.Sort s = FavoriteView.parseSort(sort);
        return FavoriteView.build(query.tracked(userId), query.tagCategories(), s, LocalDate.now(clock.withZone(KOREA)));
    }

    /** 혜택 하나에 대해, 함께 관심·신청 중인 다른 혜택과의 중복 경고 */
    public List<FavoriteResponse.Warning> warningsFor(long userId, long benefitId) {
        return FavoriteView.warningsFor(benefitId, query.tracked(userId), query.tagCategories());
    }
}
