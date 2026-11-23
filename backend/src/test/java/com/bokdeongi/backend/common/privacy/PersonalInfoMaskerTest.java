package com.bokdeongi.backend.common.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 번호는 모두 가짜 */
class PersonalInfoMaskerTest {

    @Test
    void 주민번호_휴대전화_이메일만_가린다() {
        assertEquals("[가림] / [가림] / [가림] / 문의 02-0000-0000",
                PersonalInfoMasker.mask("000101-4000000 / 010-0000-0000 / test@example.com / 문의 02-0000-0000"));
    }
}
