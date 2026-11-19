package com.bokdeongi.backend.benefit.favorite;

import com.bokdeongi.backend.benefit.favorite.DuplicateWarnings.Tracked;
import com.bokdeongi.backend.benefit.recommend.RecommendException;
import com.bokdeongi.backend.benefit.recommend.RecommendView;
import com.bokdeongi.backend.eligibility.store.JsonText;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 관심 혜택 목록(BEN-12)과 중복 경고(BEN-09)를 만든다. DB·Spring에 의존하지 않는다. */
public final class FavoriteView {

    public enum Sort { DEADLINE, SAVED }

    static final ZoneOffset KOREA = ZoneOffset.ofHours(9);
    static final Set<String> APPLYING = Set.of("IN_PROGRESS", "SUBMITTED");
    static final String FIELD_CATEGORY = "분야";

    private FavoriteView() {
    }

    public static Sort parseSort(String value) {
        if (value == null || value.isBlank()) {
            return Sort.DEADLINE;
        }
        try {
            return Sort.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RecommendException("sort는 DEADLINE 또는 SAVED입니다");
        }
    }

    public static FavoriteResponse build(List<FavoriteRow> rows, Map<String, String> tagCategories, Sort sort,
            LocalDate today) {
        Map<Long, List<DuplicateWarnings.Warning>> warnings = DuplicateWarnings.find(tracked(rows, tagCategories));
        List<FavoriteRow> favorites = new ArrayList<>(rows.stream().filter(FavoriteRow::bookmarked).toList());
        favorites.sort(comparator(sort));
        List<FavoriteResponse.Item> items = new ArrayList<>();
        List<FavoriteResponse.Item> ended = new ArrayList<>();
        for (FavoriteRow r : favorites) {
            FavoriteResponse.Item item = item(r, warnings.getOrDefault(r.card().benefitId(), List.of()), today);
            ("ACTIVE".equals(r.benefitStatus()) ? items : ended).add(item);
        }
        return new FavoriteResponse(sort.name(), items.size() + ended.size(), items, ended);
    }

    /** 혜택 하나의 중복 경고 (상세·신청 시작 화면용, BEN-09) */
    public static List<FavoriteResponse.Warning> warningsFor(long benefitId, List<FavoriteRow> rows,
            Map<String, String> tagCategories) {
        return DuplicateWarnings.find(tracked(rows, tagCategories)).getOrDefault(benefitId, List.of()).stream()
                .map(FavoriteView::warning).toList();
    }

    /** 경고 대상: 공고가 살아 있는 관심·신청 중 혜택 */
    static List<Tracked> tracked(List<FavoriteRow> rows, Map<String, String> tagCategories) {
        List<Tracked> out = new ArrayList<>();
        for (FavoriteRow r : rows) {
            if (!"ACTIVE".equals(r.benefitStatus())) {
                continue;
            }
            Set<String> fieldTags = new LinkedHashSet<>();
            for (String tag : RecommendView.hashtags(r.card().hashtagsJson())) {
                if (FIELD_CATEGORY.equals(tagCategories.get(tag))) {
                    fieldTags.add(tag);
                }
            }
            out.add(new Tracked(r.card().benefitId(), r.card().title(), r.card().serviceField(), fieldTags,
                    exclusionQuote(r.exclusionsJson())));
        }
        return out;
    }

    static String exclusionQuote(String json) {
        try {
            for (Map<String, Object> m : JsonText.objectList(json)) {
                Object q = m.get("sourceQuote");
                if (q != null && !q.toString().isBlank()) {
                    return q.toString();
                }
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    private static FavoriteResponse.Item item(FavoriteRow r, List<DuplicateWarnings.Warning> warnings,
            LocalDate today) {
        boolean applying = r.appStatus() != null && APPLYING.contains(r.appStatus());
        FavoriteResponse.LastChange change = null;
        if (r.lastChangeSummary() != null && r.lastChangedAt() != null
                && (r.bookmarkedAt() == null || r.lastChangedAt().isAfter(r.bookmarkedAt()))) {
            change = new FavoriteResponse.LastChange(r.lastChangeSummary(), korea(r.lastChangedAt()));
        }
        return new FavoriteResponse.Item(r.card().benefitId(), r.card().title(), r.card().serviceField(),
                r.card().orgName(), r.card().regionName(), r.card().result(), RecommendView.amount(r.card()),
                RecommendView.period(r.card(), today), RecommendView.hashtags(r.card().hashtagsJson()), applying,
                r.appStatus() == null ? null : r.userBenefitId(), r.appStatus(), korea(r.bookmarkedAt()), change,
                warnings.stream().map(FavoriteView::warning).toList());
    }

    private static FavoriteResponse.Warning warning(DuplicateWarnings.Warning w) {
        String text = w.level() == DuplicateWarnings.Level.STRONG
                ? "'" + w.otherTitle() + "'와(과) 함께 받을 수 없어요"
                : "'" + w.otherTitle() + "'와(과) 중복 지원이 안 될 수 있어요. 기관에 확인해 보세요";
        return new FavoriteResponse.Warning(w.otherBenefitId(), w.otherTitle(), w.level().name(), text,
                w.sourceQuote());
    }

    private static Comparator<FavoriteRow> comparator(Sort sort) {
        Comparator<FavoriteRow> saved = Comparator.comparing(FavoriteRow::bookmarkedAt,
                Comparator.nullsLast(Comparator.reverseOrder()));
        if (sort == Sort.SAVED) {
            return saved;
        }
        return Comparator.comparing((FavoriteRow r) -> r.card().applyEnd(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(saved);
    }

    private static OffsetDateTime korea(LocalDateTime utc) {
        return utc == null ? null : utc.atOffset(ZoneOffset.UTC).withOffsetSameInstant(KOREA);
    }
}
