package com.bokdeongi.backend.eligibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConditionTextTest {

    @Test
    @DisplayName("지원조건 코드는 이름으로 (소득 구간·묶음 코드)")
    void codes() {
        assertEquals("중위소득 0~50%, 중위소득 51~75%, 중위소득 76~100%",
                ConditionText.expected("INCOME_PCT", "JA0201, JA0202, JA0203"));
        assertEquals("무주택세대", ConditionText.expected("JA_GROUP:JA04", "JA04 JA0412"));
        assertEquals("임산부, 장애인", ConditionText.expected("JA_GROUP:JA03", "JA03 JA0302, JA0328"));
        assertEquals("구직자·실업자, 자영업자", ConditionText.expected("STATUS", "JA0327, SELF_EMPLOYED"));
        assertEquals("구직자·실업자", ConditionText.actual("STATUS", "JA0327"));
        assertEquals("JA9999", ConditionText.expected("STATUS", "JA9999"));
    }

    @Test
    @DisplayName("숫자 조건은 단위를 붙인다. 범위는 끝에만")
    void numbers() {
        assertEquals("만 19세 이상", ConditionText.expected("AGE", "19 이상"));
        assertEquals("만 19~34세", ConditionText.expected("AGE", "19~34"));
        assertEquals("만 35세", ConditionText.actual("AGE", "35"));
        assertEquals("중위소득 100% 이하", ConditionText.expected("INCOME_PCT", "100 이하"));
        assertEquals("중위소득 50~150%", ConditionText.expected("INCOME_PCT", "50~150"));
        assertEquals("72.5%", ConditionText.actual("INCOME_PCT", "72.5%"));
        assertEquals("60% 이하", ConditionText.expected("INCOME_PCT", "60% 이하"));
        assertEquals("2명 이하", ConditionText.expected("HOUSEHOLD_SIZE", "2 이하"));
        assertEquals("자녀 2명 이상", ConditionText.expected("CHILDREN_COUNT", "2 이상"));
        assertEquals("막내 7세 이하", ConditionText.expected("YOUNGEST_CHILD_AGE", "7 이하"));
    }

    @Test
    @DisplayName("필드별 값 이름 (같은 글자도 필드마다 뜻이 다르다)")
    void words() {
        assertEquals("여성", ConditionText.expected("GENDER", "F"));
        assertEquals("월세", ConditionText.actual("HOUSING", "MONTHLY"));
        assertEquals("전세, 월세", ConditionText.expected("HOUSING", "JEONSE, MONTHLY"));
        assertEquals("무주택", ConditionText.expected("HAS_HOUSE", "false"));
        assertEquals("기혼", ConditionText.actual("MARITAL", "MARRIED"));
        assertEquals("기초생활수급자, 차상위계층", ConditionText.expected("WELFARE", "BASIC, NEAR_POOR"));
        assertEquals("예", ConditionText.expected("ANSWER:no_house", "Y"));
        assertEquals("아니요", ConditionText.actual("ANSWER:no_house", "N"));
        assertEquals("ETC", ConditionText.expected("REGION", "ETC"));
    }

    @Test
    @DisplayName("미입력·비공개·원문 확인 문구와 지역 이름은 그대로")
    void untouched() {
        assertEquals("미입력", ConditionText.actual("AGE", "미입력"));
        assertEquals("비공개", ConditionText.actual("SENSITIVE", "비공개"));
        assertEquals("원문 확인 필요", ConditionText.expected("UNPARSED", "원문 확인 필요"));
        assertEquals("서울특별시 광진구", ConditionText.expected("REGION", "서울특별시 광진구"));
        assertEquals("-", ConditionText.actual("RULES", "-"));
        assertNull(ConditionText.expected("AGE", null));
        assertNull(ConditionText.actual(null, null));
    }
}
