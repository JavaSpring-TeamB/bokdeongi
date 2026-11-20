package com.bokdeongi.backend.extraction.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConditionFieldsTest {

    @Test
    void 코드_필드는_값이_하나여도_IN이고_대문자로_맞춘다() {
        ConditionFields.Checked c = ConditionFields.check("STATUS", "EQ", List.of("ja0320"));
        assertTrue(c.ok());
        assertEquals("IN", c.spec().operator());
        assertEquals(List.of("JA0320"), c.spec().values());
    }

    @Test
    void 목록에_없는_코드와_필드는_버린다() {
        assertFalse(ConditionFields.check("STATUS", "IN", List.of("JA0399")).ok());
        assertFalse(ConditionFields.check("INCOME", "LTE", List.of("100")).ok());
        assertFalse(ConditionFields.check("JA_GROUP:JA03", "IN", List.of("JA0328")).ok());
    }

    @Test
    void ANSWER는_key_모양과_Y_N만_받는다() {
        assertTrue(ConditionFields.check("ANSWER:public_rental", "EQ", List.of("n")).ok());
        assertEquals(List.of("N"), ConditionFields.check("ANSWER:public_rental", "EQ", List.of("n")).spec().values());
        assertFalse(ConditionFields.check("ANSWER:Public-Rental", "EQ", List.of("Y")).ok());
        assertFalse(ConditionFields.check("ANSWER:public_rental", "EQ", List.of("예")).ok());
        assertFalse(ConditionFields.check("ANSWER:" + "a".repeat(54), "EQ", List.of("Y")).ok());
    }

    @Test
    void 숫자_필드는_연산자별_값_개수와_정수를_본다() {
        assertEquals(List.of("19", "34"),
                ConditionFields.check("AGE", "BETWEEN", List.of("34", "19")).spec().values());
        assertFalse(ConditionFields.check("AGE", "BETWEEN", List.of("19")).ok());
        assertFalse(ConditionFields.check("AGE", "LTE", List.of("34.5")).ok());
        assertFalse(ConditionFields.check("AGE", "IN", List.of("19")).ok());
        assertEquals(List.of("62.5"), ConditionFields.check("INCOME_PCT", "LTE", List.of("62.50")).spec().values());
    }

    @Test
    void 불리언과_성별은_표준형으로() {
        assertEquals(List.of("false"), ConditionFields.check("HAS_HOUSE", "EQ", List.of("FALSE")).spec().values());
        assertEquals("EQ", ConditionFields.check("GENDER", "IN", List.of("f")).spec().operator());
    }

    @Test
    void 값이_비면_버린다() {
        assertFalse(ConditionFields.check("AGE", "GTE", List.of(" ")).ok());
        assertFalse(ConditionFields.check("REGION", "IN", null).ok());
    }
}
