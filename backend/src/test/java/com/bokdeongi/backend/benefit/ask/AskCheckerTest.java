package com.bokdeongi.backend.benefit.ask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class AskCheckerTest {

    /** 테스트용 가짜 공고 */
    private static final List<String> SOURCES = List.of(
            "청년 월세 지원",
            "부모와 따로 사는 만 19~34세 무주택 청년 (기준 중위소득 60% 이하)",
            "월 최대 200,000원, 최대 12개월 지원",
            "복지로 온라인 신청 또는 주소지 행정복지센터 방문",
            "국토교통부/1599-0001");

    private static String json(boolean found, String answer, String quotes, boolean eligibility) {
        return "{\"found\":" + found + ",\"answer\":\"" + answer + "\",\"quotes\":" + quotes + ",\"eligibility\":"
                + eligibility + "}";
    }

    @Test
    void 원문에_있는_근거와_숫자면_통과() {
        AskChecker.Result r = AskChecker.check(json(true, "한 달에 최대 20만 원을 12개월 동안 받아요.",
                "[\"월 최대 200,000원, 최대 12개월 지원\"]", false), SOURCES);
        assertTrue(r.ok(), r.reason());
        assertTrue(r.found());
        assertEquals(List.of("월 최대 200,000원, 최대 12개월 지원"), r.quotes());
    }

    @Test
    void 원문에_없는_근거_문장은_빼고_하나도_안_남으면_버린다() {
        AskChecker.Result r = AskChecker.check(json(true, "휴학생도 신청할 수 있어요.",
                "[\"휴학생도 신청 가능\"]", false), SOURCES);
        assertFalse(r.ok());
        assertEquals("근거 문장이 원문에 없음", r.reason());
    }

    @Test
    void 근거_일부만_원문에_있으면_있는_것만_남긴다() {
        AskChecker.Result r = AskChecker.check(json(true, "복지로나 행정복지센터에서 신청해요.",
                "[\"복지로 온라인 신청 또는 주소지 행정복지센터 방문\", \"앱으로도 가능\"]", false), SOURCES);
        assertEquals(List.of("복지로 온라인 신청 또는 주소지 행정복지센터 방문"), r.quotes());
    }

    @Test
    void 계산해서_만든_숫자는_버린다() {
        assertFalse(AskChecker.check(json(true, "1년이면 240만 원이에요.",
                "[\"월 최대 200,000원, 최대 12개월 지원\"]", false), SOURCES).ok());
    }

    @Test
    void 못_찾으면_근거는_비운다() {
        AskChecker.Result r = AskChecker.check(json(false, "공고에는 휴학생에 대한 내용이 없어요.", "[]", false), SOURCES);
        assertTrue(r.ok());
        assertFalse(r.found());
        assertEquals(List.of(), r.quotes());
    }

    @Test
    void 묻는_사람의_자격을_말하면_버린다() {
        assertTrue(AskChecker.verdict("네, 받을 수 있어요."));
        assertTrue(AskChecker.verdict("회원님은 대상이에요."));
        assertTrue(AskChecker.verdict("공고를 보면 받을 수 있어요."));
        assertTrue(AskChecker.verdict("신청할 수 있어요."));
        assertFalse(AskChecker.check(json(true, "네, 받을 수 있어요.",
                "[\"부모와 따로 사는 만 19~34세 무주택 청년 (기준 중위소득 60% 이하)\"]", true), SOURCES).ok());
    }

    @Test
    void 누가_받는지_설명은_된다() {
        assertFalse(AskChecker.verdict("부모와 따로 사는 만 19~34세 무주택 청년이 받을 수 있어요."));
        assertFalse(AskChecker.verdict("기준 중위소득 60% 이하인 청년이 대상이에요."));
        assertTrue(AskChecker.check(json(true, "부모와 따로 사는 만 19~34세 무주택 청년이 받을 수 있어요.",
                "[\"부모와 따로 사는 만 19~34세 무주택 청년 (기준 중위소득 60% 이하)\"]", true), SOURCES).eligibility());
    }

    @Test
    void 형식이_틀리면_버린다() {
        assertFalse(AskChecker.check("답: 20만 원이에요", SOURCES).ok());
        assertFalse(AskChecker.check("{\"found\":true}", SOURCES).ok());
        assertFalse(AskChecker.check(null, SOURCES).ok());
        assertTrue(AskChecker.check("```json\n" + json(false, "공고에는 그 내용이 없어요.", "[]", false) + "\n```", SOURCES).ok());
    }
}
