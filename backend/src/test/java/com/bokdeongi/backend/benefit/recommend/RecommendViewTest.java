package com.bokdeongi.backend.benefit.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bokdeongi.backend.benefit.recommend.RecommendResponse.Check;
import com.bokdeongi.backend.benefit.recommend.RecommendResponse.Item;
import com.bokdeongi.backend.benefit.recommend.RecommendView.CheckType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 판정 결과 → 화면 카드 (BEN-01·03·04·05) */
class RecommendViewTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    private static final String TRACE_ELIGIBLE = "[{\"ruleId\":1,\"field\":\"AGE\",\"expected\":\"19~34\",\"actual\":\"27\","
            + "\"outcome\":\"PASS\",\"reason\":null,\"sourceQuote\":\"만 19~34세\",\"boundary\":false,\"group\":0},"
            + "{\"ruleId\":null,\"field\":\"REGION\",\"expected\":\"서울특별시 관악구\",\"actual\":\"서울특별시 관악구\","
            + "\"outcome\":\"PASS\",\"reason\":null,\"sourceQuote\":\"소관기관 지역: 서울특별시 관악구\",\"boundary\":false,\"group\":0}]";

    private static RecommendRow row(String result, Long max, String period, LocalDate end, String dateStatus,
            String precision, Boolean online, Integer docs, String trace, boolean nearMiss, String previous) {
        return new RecommendRow(7L, "청년 월세 지원", "주거·자립", "현금", "월세 지원", max, period, max == null ? null : max * 12,
                null, end, dateStatus, precision, false, online, docs, "[\"월세\",\"청년\"]", "https://gov24/7", null,
                "관악구", "서울특별시", result, previous, nearMiss, nearMiss ? "2027년 3월 2일부터 나이 조건을 충족해요" : null,
                "[]", trace, true);
    }

    @Test
    @DisplayName("금액 문구: 만·억 단위")
    void won() {
        assertEquals("20만원", RecommendView.won(200_000));
        assertEquals("1억 2,000만원", RecommendView.won(120_000_000));
        assertEquals("5,000원", RecommendView.won(5_000));
        assertEquals("19만 2,500원", RecommendView.won(192_500));
        assertEquals("1억원", RecommendView.won(100_000_000));
    }

    @Test
    @DisplayName("해당 카드: 금액·기간·D-day·추천 이유·추천 표시(상위 3건)·신규 해당")
    void eligibleCard() {
        Item item = RecommendView.toItem(row("ELIGIBLE", 200_000L, "MONTH", LocalDate.of(2026, 11, 30), "PARSED", "DAY",
                true, 3, TRACE_ELIGIBLE, false, "CONDITIONAL"), RecommendTab.ELIGIBLE, RecommendSort.RECOMMENDED, 0, TODAY);
        assertEquals("월 최대 20만원", item.amount().text());
        assertEquals(2_400_000L, item.amount().annual());
        assertEquals("~ 2026.11.30 (D-12)", item.period().text());
        assertEquals(Integer.valueOf(12), item.period().dDay());
        assertEquals("나이·거주지 조건을 충족해요", item.reason());
        assertTrue(item.recommended());
        assertTrue(item.newlyEligible());
        assertEquals(List.of("월세", "청년"), item.hashtags());
        assertEquals(2, item.conditionResults().size());
        assertEquals(1L, item.conditionResults().get(0).get("ruleId"));
        assertEquals(0, item.conditionResults().get(1).get("group"));
        Item fourth = RecommendView.toItem(row("ELIGIBLE", null, null, null, "ALWAYS", null, null, null, "[]", false,
                null), RecommendTab.ELIGIBLE, RecommendSort.RECOMMENDED, 3, TODAY);
        assertFalse(fourth.recommended());
        assertNull(fourth.amount());
        assertEquals("상시 신청", fourth.period().text());
        assertEquals("따로 정해진 자격 조건이 없는 혜택이에요", fourth.reason());
    }

    @Test
    @DisplayName("기간 문구: 월 단위 날짜, 마감 지난 날은 D-day 문구 없음, 날짜 없음")
    void period() {
        Item month = RecommendView.toItem(row("ELIGIBLE", null, null, LocalDate.of(2026, 12, 31), "PARSED", "MONTH",
                null, null, "[]", false, null), RecommendTab.ELIGIBLE, RecommendSort.DEADLINE, 0, TODAY);
        assertEquals("~ 2026.12 (월 단위)", month.period().text());
        assertNull(month.period().dDay(), "월 단위 마감은 D-day를 내지 않는다");
        Item none = RecommendView.toItem(row("ELIGIBLE", null, null, null, "CHECK_NOTICE", null, null, null, "[]", false,
                null), RecommendTab.ELIGIBLE, RecommendSort.DEADLINE, 0, TODAY);
        assertEquals("공고 확인 필요", none.period().text());
        assertNull(none.period().dDay());
    }

    @Test
    @DisplayName("신청 난이도 배지 (BEN-03): 온라인/방문, 내가 낼 서류 수, 정보 없음")
    void applyBadge() {
        var online = RecommendView.applyBadge(row("ELIGIBLE", null, null, null, null, null, true, 2, "[]", false, null));
        assertEquals("온라인 신청", online.method());
        assertEquals("내가 낼 서류 2개", online.docText());
        var visit = RecommendView.applyBadge(row("ELIGIBLE", null, null, null, null, null, false, 0, "[]", false, null));
        assertEquals("방문 신청", visit.method());
        assertEquals("서류 없음", visit.docText());
        var unknown = RecommendView.applyBadge(row("ELIGIBLE", null, null, null, null, null, null, null, "[]", false, null));
        assertNull(unknown.method());
        assertEquals("서류 확인 필요", unknown.docText());
    }

    @Test
    @DisplayName("조건부: 확인할 조건 종류별 안내, 같은 질문은 한 번만 (BEN-04)")
    void checks() {
        String trace = "[{\"ruleId\":1,\"field\":\"ANSWER:no_house\",\"expected\":\"Y\",\"actual\":\"미입력\",\"outcome\":\"UNKNOWN\","
                + "\"reason\":\"MISSING_VALUE\",\"sourceQuote\":\"무주택자\",\"boundary\":false,\"group\":0},"
                + "{\"ruleId\":2,\"field\":\"ANSWER:no_house\",\"expected\":\"Y\",\"actual\":\"미입력\",\"outcome\":\"UNKNOWN\","
                + "\"reason\":\"MISSING_VALUE\",\"sourceQuote\":\"무주택자\",\"boundary\":false,\"group\":1},"
                + "{\"ruleId\":3,\"field\":\"INCOME_PCT\",\"expected\":\"60% 이하\",\"actual\":\"58%\",\"outcome\":\"UNKNOWN\","
                + "\"reason\":\"BOUNDARY\",\"sourceQuote\":\"중위소득 60% 이하\",\"boundary\":true,\"group\":0},"
                + "{\"ruleId\":4,\"field\":\"SENSITIVE\",\"expected\":\"JA0328\",\"actual\":\"비공개\",\"outcome\":\"UNKNOWN\","
                + "\"reason\":\"SENSITIVE_UNKNOWN\",\"sourceQuote\":\"장애인\",\"boundary\":false,\"group\":0},"
                + "{\"ruleId\":null,\"field\":\"UNPARSED\",\"expected\":\"원문 확인 필요\",\"actual\":\"-\",\"outcome\":\"UNKNOWN\","
                + "\"reason\":\"NEEDS_CHECK\",\"sourceQuote\":\"복지단체 등\",\"boundary\":false,\"group\":2},"
                + "{\"ruleId\":5,\"field\":\"HOUSEHOLD_SIZE\",\"expected\":\"2 이하\",\"actual\":\"미입력\",\"outcome\":\"UNKNOWN\","
                + "\"reason\":\"MISSING_VALUE\",\"sourceQuote\":\"2인 이하\",\"boundary\":false,\"group\":0},"
                + "{\"ruleId\":6,\"field\":\"AGE\",\"expected\":\"19 이상\",\"actual\":\"27\",\"outcome\":\"PASS\","
                + "\"reason\":null,\"sourceQuote\":\"19세 이상\",\"boundary\":false,\"group\":0}]";
        Item item = RecommendView.toItem(row("CONDITIONAL", null, null, null, null, null, null, null, trace, false, null),
                RecommendTab.CONDITIONAL, RecommendSort.RECOMMENDED, 0, TODAY);
        List<Check> checks = item.checks();
        assertEquals(5, checks.size());
        assertEquals(CheckType.QUESTION, checks.get(0).type());
        assertEquals("no_house", checks.get(0).questionKey());
        assertEquals("'무주택자'에 해당하나요?", checks.get(0).text());
        assertEquals(CheckType.AGENCY, checks.get(1).type());
        assertEquals("소득이 기준선 근처라 기관 확인이 필요해요", checks.get(1).text());
        assertEquals(CheckType.SENSITIVE, checks.get(2).type());
        assertEquals(CheckType.SOURCE, checks.get(3).type());
        assertEquals("복지단체 등", checks.get(3).sourceQuote());
        assertEquals(CheckType.PROFILE, checks.get(4).type());
        assertEquals("가구원 수 정보를 입력하면 확인할 수 있어요", checks.get(4).text());
        assertNull(item.reason());
    }

    @Test
    @DisplayName("아깝게 탈락: 충족 시점 문구와 미충족 조건 (BEN-05)")
    void nearMiss() {
        String trace = "[{\"ruleId\":1,\"field\":\"AGE\",\"expected\":\"19~34\",\"actual\":\"18\",\"outcome\":\"FAIL\","
                + "\"reason\":null,\"sourceQuote\":\"만 19~34세\",\"boundary\":false,\"group\":0}]";
        Item item = RecommendView.toItem(row("INELIGIBLE", null, null, null, "ALWAYS", null, null, null, trace, true, null),
                RecommendTab.NEAR_MISS, RecommendSort.RECOMMENDED, 0, TODAY);
        assertEquals("2027년 3월 2일부터 나이 조건을 충족해요", item.nearMiss().reason());
        assertEquals("나이", item.nearMiss().label());
        assertEquals("만 19~34세", item.nearMiss().expected());
        assertEquals("만 18세", item.nearMiss().actual());
        assertEquals("만 19~34세", item.nearMiss().sourceQuote());
    }

    @Test
    @DisplayName("탭·정렬 값: 화면 값(near-miss)과 명세 값(NEAR_MISS) 모두, 잘못된 값은 BENEFIT_002")
    void parse() {
        assertEquals(RecommendTab.NEAR_MISS, RecommendTab.parse("near-miss"));
        assertEquals(RecommendTab.ELIGIBLE, RecommendTab.parse(null));
        assertEquals(RecommendSort.DEADLINE, RecommendSort.parse("deadline"));
        try {
            RecommendTab.parse("all");
            throw new AssertionError("예외가 나야 함");
        } catch (RecommendException expected) {
            assertTrue(expected.getMessage().contains("tab"));
        }
    }

    @Test
    @DisplayName("추가 질문 답 검증: 키 형식, Y/N/UNSURE만, 개수 제한")
    void answerValidation() {
        assertEquals(java.util.Map.of("no_house", "Y"),
                AnswerService.validate(new AnswerRequest(java.util.Map.of("no_house", "Y"), null)));
        for (var bad : List.of(java.util.Map.of("NoHouse", "Y"), java.util.Map.of("no_house", "YES"), java.util.Map.<String, String>of())) {
            try {
                AnswerService.validate(new AnswerRequest(bad, 1L));
                throw new AssertionError("예외가 나야 함: " + bad);
            } catch (RecommendException expected) {
                // ok
            }
        }
    }
}
