package com.bokdeongi.backend.common.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 번호·주소는 모두 가짜 */
class PersonalInfoMaskerTest {

    @Test
    void 주민번호_휴대전화_이메일을_가린다() {
        assertEquals("[가림] / [가림] / [가림] / 문의 02-0000-0000",
                PersonalInfoMasker.mask("000101-4000000 / 010-0000-0000 / test@example.com / 문의 02-0000-0000"));
    }

    @Test
    void 계좌번호를_가리고_기관_전화와_날짜는_남긴다() {
        assertEquals("입금 계좌 [가림] (국민) / 문의 031-1234-5678 / 납부기한 2026-12-31",
                PersonalInfoMasker.mask("입금 계좌 123456-01-234567 (국민) / 문의 031-1234-5678 / 납부기한 2026-12-31"));
    }

    @Test
    void 하이픈_없는_계좌도_가리고_금액과_날짜는_남긴다() {
        assertEquals("가상계좌 [가림] / 월 1,833,000원 / 20261231 / 1833000원",
                PersonalInfoMasker.mask("가상계좌 56202012345678 / 월 1,833,000원 / 20261231 / 1833000원"));
    }

    @Test
    void 상세주소만_가리고_도로명_주소는_남긴다() {
        assertEquals("서울특별시 광진구 가상로 12, [가림]", PersonalInfoMasker.mask("서울특별시 광진구 가상로 12, 101호"));
        assertEquals("가상아파트 [가림]", PersonalInfoMasker.mask("가상아파트 101동 1203호"));
        assertEquals("광진1동 주민센터, 제3호 서식, 2호선 역", PersonalInfoMasker.mask("광진1동 주민센터, 제3호 서식, 2호선 역"));
    }
}
