package com.bokdeongi.backend.benefit.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/** OCR 서버 /text 응답 읽기. 글은 가짜 */
class OcrClientTextTest {

    @Test
    void 줄_목록을_읽는다() {
        assertEquals(List.of("납부기한 12월 31일", "900101-1******"),
                OcrClient.parseLines("{\"lines\": [\"납부기한 12월 31일\", \"900101-1******\"]}"));
    }

    @Test
    void 흐린_사진_422는_UNREADABLE() {
        DocumentCheckException e = OcrClient.fromError(422,
                "{\"detail\": {\"code\": \"UNREADABLE\", \"message\": \"사진이 흐려서 글자를 읽기 어려워요.\"}}");
        assertEquals("UNREADABLE", e.getCode());
        assertEquals(422, e.getStatus());
    }

    @Test
    void 이상한_응답은_AI_UNAVAILABLE() {
        assertEquals("AI_UNAVAILABLE", assertThrows(DocumentCheckException.class,
                () -> OcrClient.parseLines("{\"x\": 1}")).getCode());
    }
}
