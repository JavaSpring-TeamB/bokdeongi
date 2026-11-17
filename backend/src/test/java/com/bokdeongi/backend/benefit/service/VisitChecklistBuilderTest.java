package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bokdeongi.backend.benefit.service.VisitChecklistBuilder.Checklist;
import com.bokdeongi.backend.benefit.service.VisitChecklistBuilder.Contact;
import com.bokdeongi.backend.benefit.service.VisitChecklistBuilder.Input;

class VisitChecklistBuilderTest {

    private static Checklist build(String method, String docs, String receiving, String org, String contact) {
        return VisitChecklistBuilder.build(new Input(method, docs, receiving, org, contact));
    }

    @Test
    void 신분증이_맨_앞이고_구비서류가_뒤에_붙는다() {
        Checklist c = build("○ 방문 신청\n - 주민센터 : 관할 읍면동 주민센터 방문 신청",
                "한부모가족증명서, 통장사본, 신분증 사본", null, "충청남도", "여성가족정책과/041-635-2982");
        assertTrue(c.visitApply());
        assertEquals(List.of("신분증", "한부모가족증명서", "통장사본"), c.items());
    }

    @Test
    void 접수처는_신청방법의_방문_부분에서_꺼낸다() {
        assertEquals(List.of("관할 읍면동 주민센터"),
                build("○ 방문 신청\n - 주민센터 : 관할 읍면동 주민센터 방문 신청", "", null, "충청남도", "").places());
        assertEquals(List.of("관내 지역 농협"),
                build("○ 방문 신청 /  - 기타 : 관내 지역 농협", "", null, "충청남도 태안군", "").places());
        assertEquals(List.of("세종시청 청년정책담당관, 4층 408호"),
                build("① 온라인 접수( 정부24 )\n② 이메일 접수\n③ 방문 접수(세종시청 청년정책담당관, 4층 408호)", "", null, "세종특별자치시", "").places());
        assertEquals(List.of("공주시청 교통과, 읍면동 행정복지센터"),
                build("방문신청 : 공주시청 교통과, 읍면동 행정복지센터 서류접수", "", null, "충청남도 공주시", "").places());
    }

    @Test
    void 접수처_앞뒤의_절차_말과_안내_괄호를_지운다() {
        assertEquals(List.of("해당 읍면사무소"),
                build("○ 방문 신청 /  - 주민센터 : 사업신청서를 작성하여 해당 읍면사무소에 ", "", null, "", "").places());
        assertEquals(List.of("거주지 주민센터"),
                build("○ 방문 신청  /  - 주민센터 : 출생신고 후 1년 이내 거주지 주민센터에", "", null, "", "").places());
        assertEquals(List.of("주소지 읍면사무소"), build("방문신청: 주소지 읍면사무소(신분증 지참)", "", null, "", "").places());
        assertEquals(List.of("대구광역시 중구보건소 1층 모자보건실"),
                build("○ 방문 신청 - 대구광역시 중구보건소 1층 모자보건실 방문", "", null, "", "").places());
        assertEquals(List.of("돌발 재해 피해 토지 소재지 해당읍면"),
                build("○ 방문 신청  /  - 주민센터 : 돌발 재해 피해 토지 소재지 해당읍면 방문 ", "", null, "", "").places());
        assertEquals(List.of("거주지역 읍면동 주민센터"), build("○ 방문 신청 /  - 거주지역 읍면동 주민센터 직접방문", "", null, "", "").places());
        assertEquals(List.of("관할 아동 거주지 읍면동 주민센터"),
                build("○ 방문 신청 /  - 주민센터 : 관할 아동 거주지 읍면동 주민센터에 문의 및 방문 ", "", null, "", "").places());
    }

    @Test
    void 기간_서류_안내_줄은_접수처가_아니다() {
        assertEquals(List.of("평생학습관"),
                build("○ 방문 신청 /  - 기타 : 평생학습관 /  - 분기별 1과목 수강료 전액 감면", "", null, "", "").places());
        assertEquals(List.of("특수교육대상자가 재학중인 학교(유치원)"),
                build("○ 방문신청 /  - 신청기간 : 정기신청(3월 중) 및 수시신청(신규 선정배치 시) /  - 신청방법 : 특수교육대상자가 재학중인 학교(유치원)에 신청 /  - 제출서류 : 신청서 및 관련 증빙",
                        "", null, "", "").places());
    }

    @Test
    void 다른_신청_방법은_접수처에_넣지_않는다() {
        Checklist c = build("○ 방문 신청  - 기타 : 관내 판매대행점(농협, 축협)\n○ 모바일 신청 : chak 어플 다운 및 회원가입후 신청",
                "", null, "강원특별자치도 정선군", "");
        assertEquals(List.of("관내 판매대행점(농협, 축협)"), c.places());
    }

    @Test
    void 접수기관_칸이_있으면_그것을_쓴다() {
        assertEquals(List.of("○○구청 복지정책과"),
                build("○ 방문 신청", "", "○○구청 복지정책과", "서울특별시 ○○구", "").places());
    }

    @Test
    void 접수처를_못_찾으면_소관기관으로_대신한다() {
        Checklist c = build("○ 직접방문(팩스,우편접수불가)\n○ 지급시기 : 신청한 익월", "", null, "경상북도 청도군", "");
        assertTrue(c.visitApply());
        assertEquals(List.of("경상북도 청도군"), c.places());
        assertTrue(c.placeFromOrg());
    }

    @Test
    void 온라인만_있으면_방문_신청이_아니다() {
        assertFalse(build("- 청년노동자 통장 홈페이지 온라인신청(https://account.gfgf.kr)", "", null, "경기도", "").visitApply());
    }

    @Test
    void 문의처를_부서와_전화로_나눈다() {
        assertEquals(List.of(new Contact("경제과", "033-560-2324"), new Contact("민원실", "033-560-2000")),
                build("○ 방문 신청", "", null, "", "경제과/033-560-2324||민원실/033-560-2000").contacts());
        assertEquals(List.of(new Contact("(국번없이)132 법률상담 콜센타", "132")),
                build("○ 방문 신청", "", null, "", "(국번없이)132 법률상담 콜센타/132").contacts());
    }
}
