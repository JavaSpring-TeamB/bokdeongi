package com.bokdeongi.backend.eligibility.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonTextTest {

    @Test
    @DisplayName("값 목록: 숫자·문자 배열, NULL(건너뜀)과 [](해당 없음) 구분 (D-45)")
    void stringList() {
        assertEquals(List.of("19", "34"), JsonText.stringList("[19, 34]"));
        assertEquals(List.of("JA0317", "JA0327"), JsonText.stringList("[\"JA0317\",\"JA0327\"]"));
        assertEquals(List.of("0.5"), JsonText.stringList("[0.5]"));
        assertNull(JsonText.stringList(null));
        assertNull(JsonText.stringList("null"));
        assertEquals(List.of(), JsonText.stringList("[]"));
    }

    @Test
    @DisplayName("추가 질문 답과 규칙으로 못 바꾼 조건")
    void mapsAndObjects() {
        assertEquals(Map.of("no_house", "Y", "doctor", "UNSURE"),
                JsonText.stringMap("{\"no_house\": \"Y\", \"doctor\": \"UNSURE\"}"));
        assertTrue(JsonText.stringMap(null).isEmpty());
        List<Map<String, Object>> u = JsonText.objectList(
                "[{\"sourceQuote\": \"의사 판단하에 \\\"필요한\\\" 자\", \"ruleGroup\": 2}]");
        assertEquals("의사 판단하에 \"필요한\" 자", u.get(0).get("sourceQuote"));
        assertEquals("2", u.get(0).get("ruleGroup"));
    }

    @Test
    @DisplayName("쓰기: 따옴표·줄바꿈 이스케이프, 읽으면 같은 값")
    void roundTrip() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ruleId", 7L);
        m.put("sourceQuote", "만 19~34세\n\"청년\"");
        m.put("reason", null);
        m.put("boundary", false);
        StringBuilder sb = new StringBuilder("[");
        JsonText.writeObject(sb, m);
        String json = sb.append(']').toString();
        assertEquals("[{\"ruleId\":7,\"sourceQuote\":\"만 19~34세\\n\\\"청년\\\"\",\"reason\":null,\"boundary\":false}]", json);
        assertEquals("만 19~34세\n\"청년\"", JsonText.objectList(json).get(0).get("sourceQuote"));
        assertEquals("[\"INCOME_PCT\",\"AGE\"]", JsonText.writeStrings(List.of("INCOME_PCT", "AGE")));
    }
}
