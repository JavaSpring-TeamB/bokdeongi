package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.bokdeongi.backend.benefit.service.CopyCardBuilder.CopyCard;
import com.bokdeongi.backend.profile.dto.response.ProfileResponse;

class CopyCardBuilderTest {

    /** 테스트용 가짜 회원 (실제 개인정보 아님) */
    private static ProfileResponse profile(String name, String phone, Short household, String housing, Short children) {
        return new ProfileResponse(1L, name, "test@example.com", phone,
                LocalDate.of(2000, 1, 2), "F", "11215", "서울특별시", "광진구",
                household, "BRACKET", (short) 2026, null, housing, true, List.of(),
                List.of(), "SINGLE", children, null, "NONE",
                1, (short) 4, true, null);
    }

    @Test
    void 값이_있는_항목만_순서대로() {
        List<CopyCard> cards = CopyCardBuilder.build(profile("김복덩", "010-0000-0000", (short) 1, "MONTHLY", null));
        assertEquals(List.of("이름", "생년월일", "성별", "휴대전화", "이메일", "거주지(시·군·구)", "가구원 수", "주거 형태", "혼인 상태"),
                cards.stream().map(CopyCard::label).toList());
        assertEquals(List.of("김복덩", "2000-01-02", "여성", "010-0000-0000", "test@example.com", "서울특별시 광진구",
                "1명", "월세", "미혼"), cards.stream().map(CopyCard::value).toList());
    }

    @Test
    void 자녀가_0명이어도_적는다() {
        List<CopyCard> cards = CopyCardBuilder.build(profile("김복덩", null, null, null, (short) 0));
        assertEquals("0명", cards.get(cards.size() - 1).value());
        assertEquals("자녀 수", cards.get(cards.size() - 1).label());
    }

    @Test
    void 주민등록번호_모양이_섞인_값은_카드로_만들지_않는다() {
        List<CopyCard> cards = CopyCardBuilder.build(profile("김복덩 000102-4000000", null, null, null, null));
        assertEquals(List.of(), cards.stream().filter(c -> c.key().equals("name")).toList());
    }

    @Test
    void 프로필이_없으면_빈_목록() {
        assertEquals(List.of(), CopyCardBuilder.build(null));
    }
}
