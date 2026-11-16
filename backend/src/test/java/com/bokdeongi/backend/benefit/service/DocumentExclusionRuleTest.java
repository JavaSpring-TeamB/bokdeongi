package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DocumentExclusionRuleTest {

    @Test
    void 서류_목록을_나눈다() {
        assertEquals(List.of("성적 증명서", "자기소개서"),
                DocumentRuleExtractor.extract("성적 증명서, 자기소개서"));
        assertEquals(List.of("참가신청서", "통장사본", "주민등록등본"),
                DocumentRuleExtractor.extract("○ 참가신청서\n\n○ 통장사본\n\n○ 주민등록등본"));
    }

    @Test
    void 괄호_안_쉼표는_나누지_않고_괄호_설명은_지운다() {
        assertEquals(List.of("월세지원 신청서", "가족관계증명서", "통장사본"),
                DocumentRuleExtractor.extract("ㅇ (필수서류) 월세지원 신청(변경)서, 가족관계증명서(원가구 구성원, 청년본인 포함), 청년 본인의 통장사본"));
    }

    @Test
    void 서류가_없으면_빈_목록() {
        assertTrue(DocumentRuleExtractor.extract("구비서류없음, 학교에서 확인하여 지급함").isEmpty());
        assertTrue(DocumentRuleExtractor.extract("별도 제출 불필요(기존 급여 계좌 연계)").isEmpty());
        assertTrue(DocumentRuleExtractor.extract("해당없음").isEmpty());
        assertTrue(DocumentRuleExtractor.extract("").isEmpty());
    }

    @Test
    void 안내_대리인_담당자_줄은_뺀다() {
        assertEquals(List.of("사업신청서"),
                DocumentRuleExtractor.extract("민원인: 사업신청서\n담당자: 주민등록 및 체납여부 확인"));
        assertEquals(List.of("신분증", "통장사본"),
                DocumentRuleExtractor.extract("- 신분증, 통장사본\n- (대리인 접수) 대리인 신분증 및 위임장 추가 필요\n※ 서류 발급일 기준은 공고일 이후"));
    }

    @Test
    void 중복_불가_문구를_찾는다() {
        assertEquals("장애인활동지원, 가사·간병서비스 등 유사 서비스와 중복지원 불가",
                ExclusionRuleExtractor.extract("○ 여성장애인\n- 장애인활동지원, 가사·간병서비스 등 유사 서비스와 중복지원 불가", "", ""));
        assertEquals("유사사업 중복지원 제외: 산모‧신생아 건강관리 지원",
                ExclusionRuleExtractor.extract("", "", "※ 유사사업 중복지원 제외: 산모‧신생아 건강관리 지원"));
    }

    @Test
    void 중복_가능이나_중복장애는_아니다() {
        assertEquals("", ExclusionRuleExtractor.extract("※ 개인 실손보험과 중복보장 가능", "1순위 장애인연금 수급자 또는 중복장애인", ""));
    }
}
