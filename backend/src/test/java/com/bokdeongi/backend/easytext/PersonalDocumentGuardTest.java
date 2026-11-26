package com.bokdeongi.backend.easytext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 글은 모두 가짜 */
class PersonalDocumentGuardTest {

    @Test
    void 제목이_개인_서류면_막는다() {
        assertTrue(PersonalDocumentGuard.looksPersonal("주민등록표\n(등 본)\n세대주 성명 김복덩"));
        assertTrue(PersonalDocumentGuard.looksPersonal("가 족 관 계 증 명 서\n본인 김복덩"));
        assertTrue(PersonalDocumentGuard.looksPersonal("[시연용 견본]\n건강보험료 납부확인서\n가입자 김복덩"));
        assertTrue(PersonalDocumentGuard.looksPersonal("\n\n주민등록초본\n"));
    }

    @Test
    void 고지서_통지서는_본문에_서류_이름이_있어도_통과() {
        assertFalse(PersonalDocumentGuard.looksPersonal(
                "긴급복지 지원 결정 통지서\n수신 : 김복덩 귀하\n신청 시 소득금액증명원과 주민등록등본을 내세요."));
        assertFalse(PersonalDocumentGuard.looksPersonal(
                "신청하실 때는 주민등록등본과 건강보험료 납부확인서를 함께 내셔야 하며 기한은 12월 31일까지입니다."));
        assertFalse(PersonalDocumentGuard.looksPersonal("소득금액증명원 제출 안내\n제출 기한 12월 31일"));
    }

    @Test
    void 앞쪽_3줄_뒤는_안_본다() {
        assertFalse(PersonalDocumentGuard.looksPersonal("납부 안내\n납부기한 12월 31일\n문의 120\n준비 서류\n주민등록등본"));
    }
}
