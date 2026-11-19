package com.bokdeongi.backend.benefit.recommend;

import com.bokdeongi.backend.benefit.recommend.RecommendQuery.Filter;
import com.bokdeongi.backend.benefit.recommend.RecommendQuery.FilterOptions;
import com.bokdeongi.backend.benefit.recommend.RecommendQuery.Freshness;
import com.bokdeongi.backend.benefit.recommend.RecommendResponse.Filters;
import com.bokdeongi.backend.benefit.recommend.RecommendResponse.Item;
import com.bokdeongi.backend.eligibility.service.EligibilityService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 맞춤 혜택 목록 (BEN-01·02·03·05). 판정은 프로필 저장 때 미리 해 두고(ELIG-01), 여기서는 저장된 결과를 읽는다.
 */
@Service
public class RecommendService {

    static final int MAX_SIZE = 50;
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final RecommendQuery query;
    private final EligibilityService eligibility;

    public RecommendService(RecommendQuery query, EligibilityService eligibility) {
        this.query = query;
        this.eligibility = eligibility;
    }

    public RecommendResponse recommended(long userId, String tabValue, String region, String field, String sortValue,
            int page, int size) {
        return recommended(userId, tabValue, region, field, null, sortValue, page, size);
    }

    /** @param hashtag 해시태그 이름 하나로 거르기 (BEN-10). null이면 전체 */
    public RecommendResponse recommended(long userId, String tabValue, String region, String field, String hashtag,
            String sortValue, int page, int size) {
        RecommendTab tab = RecommendTab.parse(tabValue);
        RecommendSort sort = RecommendSort.parse(sortValue);
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new RecommendException("page는 0 이상, size는 1~" + MAX_SIZE + "입니다");
        }
        Filter filter = new Filter(blankToNull(region), blankToNull(field), blankToNull(hashtag));

        Freshness fresh = query.freshness(userId);
        if (fresh.resultCount() == 0) {
            // 판정 결과가 하나도 없으면(가입 직후·테스트 회원) 한 번은 여기서 바로 판정한다
            eligibility.judgeUser(userId);
            fresh = query.freshness(userId);
        }
        boolean updating = fresh.oldestResultVersion() != null && fresh.oldestResultVersion() < fresh.profileVersion();

        Map<RecommendTab, Long> counts = query.counts(userId, filter);
        long total = counts.getOrDefault(tab, 0L);
        LocalDate today = LocalDate.now(KOREA);
        List<Item> items = new ArrayList<>();
        long rank = (long) page * size;
        for (RecommendRow row : query.page(userId, tab, filter, sort, page, size)) {
            items.add(RecommendView.toItem(row, tab, sort, rank++, today));
        }
        FilterOptions options = query.options(userId, tab);
        Map<String, Long> countView = new LinkedHashMap<>();
        counts.forEach((t, c) -> countView.put(t.name(), c));
        return new RecommendResponse(tab.name(), sort.name(), page, size, total,
                (int) ((total + size - 1) / size), countView, updating,
                new Filters(options.regions(), options.fields()), items);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() || "all".equalsIgnoreCase(value.strip()) ? null : value.strip();
    }
}
