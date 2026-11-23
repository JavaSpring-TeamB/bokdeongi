package com.bokdeongi.backend.easytext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 원문·모델 출력은 가짜 */
class EasyTextCheckerTest {

    private static final String SOURCE = "신청인은 공고일 기준 부산광역시에 주민등록을 두고 실제 거주 중이어야 하며, "
            + "가구의 월평균 소득이 기준 중위소득 150% 이하여야 한다. 납부기한은 2026. 12. 31.까지입니다.";

    @Test
    void 원문에_있는_묶음만_통과한다() {
        String json = """
                {"summary":"부산에 살고 소득 기준에 맞아야 해요.",
                 "sections":[
                  {"category":"소득 조건","original":"가구의 월평균 소득이 기준 중위소득 150% 이하여야 한다",
                   "simplified":"가구의 한 달 평균 소득이 기준 중위소득(나라에서 정한 가운데 소득)의 150% 이하여야 해요.",
                   "action":null,"caution":null},
                  {"category":"지어낸 것","original":"만 65세 이상만 신청할 수 있다","simplified":"65세 이상이어야 해요."}
                 ]}""";
        EasyTextChecker.Result r = EasyTextChecker.check(json, SOURCE);
        assertTrue(r.ok());
        assertEquals(1, r.sections().size());
        assertEquals("소득 조건", r.sections().get(0).category());
        assertNull(r.sections().get(0).action());
    }

    @Test
    void 묶음이_전부_원문에_없으면_버린다() {
        String json = """
                {"summary":"요약","sections":[{"category":"x","original":"원문에 없는 문장","simplified":"쉬운 말"}]}""";
        assertFalse(EasyTextChecker.check(json, SOURCE).ok());
    }

    @Test
    void 원문에_없는_숫자는_버린다() {
        String json = """
                {"summary":"한 달 소득이 300만 원 이하여야 해요.",
                 "sections":[{"category":"소득","original":"기준 중위소득 150% 이하","simplified":"150% 이하여야 해요."}]}""";
        EasyTextChecker.Result r = EasyTextChecker.check(json, SOURCE);
        assertFalse(r.ok());
    }

    @Test
    void 읽는_사람의_자격을_말하면_버린다() {
        String json = """
                {"summary":"당신은 대상이에요.",
                 "sections":[{"category":"소득","original":"기준 중위소득 150% 이하","simplified":"150% 이하여야 해요."}]}""";
        assertFalse(EasyTextChecker.check(json, SOURCE).ok());
    }

    @Test
    void 공공_안내문이_아니면_묶음_없이_요약만() {
        String json = """
                ```json
                {"summary":"공공기관 안내문이 아니라서 바꾸지 않았어요.","sections":[]}
                ```""";
        EasyTextChecker.Result r = EasyTextChecker.check(json, "오늘 저녁 뭐 먹지");
        assertTrue(r.ok());
        assertTrue(r.sections().isEmpty());
    }

    @Test
    void JSON이_아니면_버린다() {
        assertFalse(EasyTextChecker.check("쉬운 말입니다", SOURCE).ok());
        assertFalse(EasyTextChecker.check(null, SOURCE).ok());
    }
}
