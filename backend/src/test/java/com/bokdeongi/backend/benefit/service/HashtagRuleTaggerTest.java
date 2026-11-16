package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class HashtagRuleTaggerTest {

    private static List<String> tag(String title, String field, String type, Set<String> codes,
                                    Integer ageMin, Integer ageMax) {
        return HashtagRuleTagger.tag(new HashtagRuleTagger.Input(
                title, field, type, "개인", "", "", codes, ageMin, ageMax));
    }

    @Test
    void 분야_지원형태_대상을_차례로_붙인다() {
        List<String> tags = tag("청년월세 지원", "생활안정", "현금", Set.of(), 19, 34);
        assertEquals(List.of("월세", "생활비", "현금지원", "청년"), tags);
    }

    @Test
    void 지원유형으로_지원형태를_정한다() {
        assertTrue(tag("x", "생활안정", "현금(융자)", Set.of(), null, null).contains("대출"));
        assertTrue(tag("x", "생활안정", "현금(감면)", Set.of(), null, null).contains("감면"));
        assertTrue(tag("x", "생활안정", "이용권", Set.of(), null, null).contains("바우처"));
        assertTrue(tag("x", "보호·돌봄", "서비스(돌봄)", Set.of(), null, null).contains("서비스"));
        assertTrue(tag("x", "보육·교육", "현금(장학금)", Set.of(), null, null).contains("장학금"));
    }

    @Test
    void 지원조건_코드가_대상을_좁힐_때만_대상_태그() {
        // JA03 중 장애인만 Y → 장애인
        assertTrue(tag("x", "보호·돌봄", "서비스(돌봄)", Set.of("JA0328"), null, null).contains("장애인"));
        // "해당사항 없음"(JA0322)도 Y면 누구나라서 붙이지 않는다
        assertFalse(tag("x", "보호·돌봄", "서비스(돌봄)", Set.of("JA0328", "JA0322"), null, null).contains("장애인"));
    }

    @Test
    void 나이로_청년_노인을_정한다() {
        assertTrue(tag("x", "고용·창업", "현금", Set.of(), 18, 39).contains("청년"));
        assertTrue(tag("x", "보호·돌봄", "현물", Set.of(), 65, 120).contains("노인"));
        assertFalse(tag("x", "보건·의료", "현금", Set.of(), 15, 45).contains("청년"));
    }

    @Test
    void 서비스명_낱말로_좁은_분야를_붙이고_지원내용은_보지_않는다() {
        assertTrue(tag("신혼부부 전세자금 대출", "주거·자립", "현금(융자)", Set.of(), null, null).contains("전세"));
        List<String> tags = HashtagRuleTagger.tag(new HashtagRuleTagger.Input(
                "출생축하 쌀케이크", "임신·출산", "현물", "개인", "", "택배·버스 배송", Set.of(), null, null));
        assertFalse(tags.contains("교통"));
    }

    @Test
    void 소상공인은_사용자구분으로() {
        List<String> tags = HashtagRuleTagger.tag(new HashtagRuleTagger.Input(
                "x", "고용·창업", "현금", "소상공인", "", "", Set.of(), null, null));
        assertTrue(tags.contains("소상공인"));
    }

    @Test
    void 최대_5개() {
        List<String> tags = HashtagRuleTagger.tag(new HashtagRuleTagger.Input(
                "청년 신혼부부 월세 전세 창업", "주거·자립", "현금", "개인",
                "장애인 한부모 다문화 노인", "", Set.of(), null, null));
        assertTrue(tags.size() <= 5);
    }
}
