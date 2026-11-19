package com.bokdeongi.backend.benefit.easy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EasyCheckerTest {

    /** 테스트용 가짜 공고 */
    private static final List<String> SOURCES = List.of(
            "청년 월세 지원",
            "부모와 따로 사는 만 19~34세 무주택 청년 (기준 중위소득 60% 이하)",
            "월 최대 200,000원, 최대 12개월 지원",
            "복지로 온라인 신청 또는 주소지 행정복지센터 방문",
            "2026.12.31.까지",
            "국토교통부/1599-0001");

    private static String json(String who, String what, String text) {
        return "{\"who\":\"" + who + "\",\"what\":\"" + what + "\",\"how\":\"복지로나 행정복지센터에서 신청해요\","
                + "\"when\":\"2026년 12월 31일까지\",\"text\":\"" + text + "\"}";
    }

    @Test
    void 원문_숫자만_쓰면_통과() {
        EasyChecker.Result r = EasyChecker.check(json("부모와 따로 사는 만 19~34세 청년", "월세를 한 달에 최대 20만 원, 12개월 받아요",
                "청년이 월세를 덜 내도록 돕는 지원이에요. 기준 중위소득(나라에서 정한 가운데 소득) 60% 이하여야 해요. 문의는 1599-0001이에요."),
                SOURCES);
        assertTrue(r.ok(), r.reason());
        assertEquals("부모와 따로 사는 만 19~34세 청년", r.easy().who());
    }

    @Test
    void 계산해서_만든_숫자는_버린다() {
        EasyChecker.Result r = EasyChecker.check(json("만 19~34세 청년", "1년에 최대 240만 원을 받아요", "월세 지원이에요."), SOURCES);
        assertFalse(r.ok());
        assertTrue(r.reason().contains("2400000"), r.reason());
    }

    @Test
    void 읽는_사람의_자격을_말하면_버린다() {
        assertFalse(EasyChecker.check(json("만 19~34세 청년", "월세 지원", "당신은 이 지원을 받을 수 있어요."), SOURCES).ok());
        assertFalse(EasyChecker.check(json("만 19~34세 청년", "월세 지원", "회원님은 대상이에요."), SOURCES).ok());
    }

    @Test
    void 누가_받는지_설명은_된다() {
        assertTrue(EasyChecker.check(json("만 19~34세 청년", "월세 지원", "만 19~34세 청년이 받을 수 있어요."), SOURCES).ok());
    }

    @Test
    void 코드블록으로_감싸도_읽는다() {
        assertTrue(EasyChecker.check("```json\n" + json("청년", "월세 지원", "월세 지원이에요.") + "\n```", SOURCES).ok());
    }

    @Test
    void 형식이_틀리면_버린다() {
        assertFalse(EasyChecker.check("쉬운 말: 월세 지원이에요", SOURCES).ok());
        assertFalse(EasyChecker.check("[\"월세\"]", SOURCES).ok());
        assertFalse(EasyChecker.check("{\"who\":\"청년\"}", SOURCES).ok());
        assertFalse(EasyChecker.check(null, SOURCES).ok());
    }

    @Test
    void 요약이_100자를_넘으면_버린다() {
        assertFalse(EasyChecker.check(json("가".repeat(101), "월세 지원", "월세 지원이에요."), SOURCES).ok());
    }

    @Test
    void 기간이_없으면_when은_null() {
        EasyChecker.Result r = EasyChecker.check(
                "{\"who\":\"청년\",\"what\":\"월세 지원\",\"how\":\"복지로\",\"when\":null,\"text\":\"월세 지원이에요.\"}", SOURCES);
        assertTrue(r.ok());
        assertNull(r.easy().when());
    }
}
