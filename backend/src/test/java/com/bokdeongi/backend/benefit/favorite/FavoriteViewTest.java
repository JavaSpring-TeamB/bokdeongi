package com.bokdeongi.backend.benefit.favorite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bokdeongi.backend.benefit.favorite.DuplicateWarnings.Level;
import com.bokdeongi.backend.benefit.favorite.DuplicateWarnings.Tracked;
import com.bokdeongi.backend.benefit.recommend.RecommendException;
import com.bokdeongi.backend.benefit.recommend.RecommendRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FavoriteViewTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    private static final Map<String, String> CATEGORIES = Map.of("월세", "분야", "주거", "분야", "청년", "대상",
            "현금지원", "지원형태");

    private static FavoriteRow row(long id, String title, String field, String tags, LocalDate end, String status,
            boolean bookmarked, LocalDateTime savedAt, String appStatus, String exclusions, String benefitStatus) {
        RecommendRow card = new RecommendRow(id, title, field, "현금", null, 200_000L, "MONTH", 2_400_000L, null, end,
                end == null ? "ALWAYS" : "PARSED", end == null ? null : "DAY", false, true, 2, tags, null, null,
                "국토교통부", "전국", status, null, false, null, null, "[]", bookmarked);
        return new FavoriteRow(card, 100 + id, bookmarked, savedAt, appStatus, benefitStatus, exclusions, null, null);
    }

    @Test
    void 같은_분야이고_한쪽에_중복_불가_문구가_있으면_약한_경고() {
        Map<Long, List<DuplicateWarnings.Warning>> w = DuplicateWarnings.find(List.of(
                new Tracked(1, "청년월세 지원", "주거·자립", Set.of("월세"), "타 월세 지원사업과 중복 지원 불가"),
                new Tracked(2, "서울 청년 월세 지원", "주거·자립", Set.of("월세"), null)));
        assertEquals(Level.CHECK, w.get(1L).get(0).level());
        assertEquals(2L, w.get(1L).get(0).otherBenefitId());
        assertEquals(1L, w.get(2L).get(0).otherBenefitId());
        assertEquals(1L, w.get(2L).get(0).quoteBenefitId());
    }

    @Test
    void 문구에_상대_이름이_있으면_분야가_달라도_강한_경고() {
        Map<Long, List<DuplicateWarnings.Warning>> w = DuplicateWarnings.find(List.of(
                new Tracked(1, "여성장애인 가사도우미", "보호·돌봄", Set.of(), "장애인활동지원 서비스와 중복 불가"),
                new Tracked(2, "장애인활동지원", "보건·의료", Set.of(), null)));
        assertEquals(Level.STRONG, w.get(2L).get(0).level());
    }

    @Test
    void 분야가_다르고_이름도_없으면_경고하지_않는다() {
        assertTrue(DuplicateWarnings.find(List.of(
                new Tracked(1, "청년월세 지원", "주거·자립", Set.of("월세"), "타 사업과 중복 불가"),
                new Tracked(2, "문화누리카드", "문화·환경", Set.of(), null))).isEmpty());
    }

    @Test
    void 중복_불가_문구가_없으면_경고하지_않는다() {
        assertTrue(DuplicateWarnings.find(List.of(
                new Tracked(1, "A 월세", "주거·자립", Set.of("월세"), null),
                new Tracked(2, "B 월세", "주거·자립", Set.of("월세"), null))).isEmpty());
    }

    @Test
    void 마감순_마감일_없으면_맨_뒤_종료는_따로() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 1, 0, 0);
        FavoriteResponse r = FavoriteView.build(List.of(
                row(1, "상시", "생활안정", "[]", null, "ELIGIBLE", true, t, null, "[]", "ACTIVE"),
                row(2, "12월 마감", "생활안정", "[]", LocalDate.of(2026, 12, 31), "CONDITIONAL", true, t, null, "[]",
                        "ACTIVE"),
                row(3, "11월 마감", "생활안정", "[]", LocalDate.of(2026, 11, 30), null, true, t, null, "[]", "ACTIVE"),
                row(4, "끝난 공고", "생활안정", "[]", LocalDate.of(2026, 3, 31), "ELIGIBLE", true, t, null, "[]",
                        "EXPIRED"),
                row(5, "신청만 하는 중", "생활안정", "[]", null, "ELIGIBLE", false, null, "IN_PROGRESS", "[]", "ACTIVE")),
                CATEGORIES, FavoriteView.Sort.DEADLINE, TODAY);
        assertEquals(List.of("11월 마감", "12월 마감", "상시"), r.items().stream().map(FavoriteResponse.Item::title).toList());
        assertEquals(List.of("끝난 공고"), r.ended().stream().map(FavoriteResponse.Item::title).toList());
        assertEquals(4, r.count());
        assertNull(r.items().get(0).status(), "판정 전이면 배지 없음");
        assertEquals(Integer.valueOf(11), r.items().get(0).period().dDay());
    }

    @Test
    void 신청_중_배지와_변경_요약과_경고가_카드에_붙는다() {
        LocalDateTime saved = LocalDateTime.of(2026, 11, 1, 0, 0);
        FavoriteRow changed = new FavoriteRow(
                row(1, "청년월세 지원", "주거·자립", "[\"월세\",\"청년\"]", null, "ELIGIBLE", true, saved, "SUBMITTED",
                        "[{\"sourceQuote\":\"타 월세 지원사업과 중복 지원 불가\"}]", "ACTIVE").card(),
                101, true, saved, "SUBMITTED", "ACTIVE", "[{\"sourceQuote\":\"타 월세 지원사업과 중복 지원 불가\"}]",
                "신청기한이 변경됐어요", LocalDateTime.of(2026, 11, 10, 0, 0));
        FavoriteResponse r = FavoriteView.build(List.of(changed,
                        row(2, "서울 월세", "생활안정", "[\"월세\"]", null, "ELIGIBLE", false, null, "IN_PROGRESS", "[]",
                                "ACTIVE")),
                CATEGORIES, FavoriteView.Sort.SAVED, TODAY);
        FavoriteResponse.Item item = r.items().get(0);
        assertTrue(item.applying());
        assertEquals(Long.valueOf(101), item.applicationId());
        assertEquals("신청기한이 변경됐어요", item.lastChange().summary());
        assertEquals(9, item.lastChange().changedAt().getHour()); // 한국 시각
        assertEquals("CHECK", item.warnings().get(0).level());
        assertEquals(2L, item.warnings().get(0).otherBenefitId(), "관심 저장 안 하고 신청만 하는 혜택과도 경고");
    }

    @Test
    void 관심_저장_전의_변경은_보여_주지_않는다() {
        LocalDateTime saved = LocalDateTime.of(2026, 11, 15, 0, 0);
        FavoriteRow r = new FavoriteRow(row(1, "A", "생활안정", "[]", null, "ELIGIBLE", true, saved, null, "[]",
                "ACTIVE").card(), 101, true, saved, null, "ACTIVE", "[]", "신청기한이 변경됐어요",
                LocalDateTime.of(2026, 11, 10, 0, 0));
        assertNull(FavoriteView.build(List.of(r), CATEGORIES, FavoriteView.Sort.DEADLINE, TODAY).items().get(0)
                .lastChange());
    }

    @Test
    void 종료된_혜택과는_경고하지_않는다() {
        String ex = "[{\"sourceQuote\":\"타 월세 지원사업과 중복 지원 불가\"}]";
        List<FavoriteRow> rows = List.of(
                row(1, "청년월세 지원", "주거·자립", "[\"월세\"]", null, "ELIGIBLE", true, null, null, ex, "ACTIVE"),
                row(2, "서울 월세", "주거·자립", "[\"월세\"]", null, "ELIGIBLE", true, null, null, "[]", "ENDED"));
        assertTrue(FavoriteView.warningsFor(1, rows, CATEGORIES).isEmpty());
    }

    @Test
    void 정렬_값_오류() {
        assertThrows(RecommendException.class, () -> FavoriteView.parseSort("price"));
        assertEquals(FavoriteView.Sort.SAVED, FavoriteView.parseSort("saved"));
    }
}
