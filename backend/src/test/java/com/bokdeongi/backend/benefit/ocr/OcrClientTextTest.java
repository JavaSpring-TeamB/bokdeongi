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
    void 개인_서류_422는_PERSONAL_DOCUMENT() {
        DocumentCheckException e = OcrClient.fromError(422,
                "{\"detail\": {\"code\": \"PERSONAL_DOCUMENT\", \"message\": \"주민등록등본 같은 개인 서류는 올릴 수 없어요.\"}}");
        assertEquals("PERSONAL_DOCUMENT", e.getCode());
        assertEquals(422, e.getStatus());
        assertEquals("주민등록등본 같은 개인 서류는 올릴 수 없어요.", e.getMessage());
    }

    @Test
    void 이상한_응답은_AI_UNAVAILABLE() {
        assertEquals("AI_UNAVAILABLE", assertThrows(DocumentCheckException.class,
                () -> OcrClient.parseLines("{\"x\": 1}")).getCode());
    }
}
