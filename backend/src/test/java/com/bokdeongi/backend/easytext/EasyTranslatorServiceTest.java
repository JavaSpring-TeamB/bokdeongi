package com.bokdeongi.backend.easytext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import com.bokdeongi.backend.benefit.easy.EasyGenerator;
import com.bokdeongi.backend.benefit.ocr.OcrClient;
import java.util.List;
import org.springframework.mock.web.MockMultipartFile;

/** LLM은 흉내만 낸다. 붙여 넣은 글은 가짜 (D-92: 실제 개인정보 없음) */
class EasyTranslatorServiceTest {

    private static final String TEXT = "납부기한은 2026. 12. 31.까지입니다. 문의: 홍길동 010-0000-0000, 000101-4000000";
    private static final String GOOD = """
            {"summary":"12월 31일까지 내야 해요.",
             "sections":[{"category":"납부 기한","original":"납부기한은 2026. 12. 31.까지입니다.",
                          "simplified":"2026년 12월 31일까지 내야 해요.","action":"기한 안에 내세요.","caution":null}]}""";

    private ObjectProvider<ChatModel> provider;
    private EasyGenerator generator;
    private EasyTextLimiter limiter;
    private EasyTranslatorService service;
    private OcrClient ocrClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(ChatModel.class));
        generator = mock(EasyGenerator.class);
        limiter = new EasyTextLimiter();
        ocrClient = mock(OcrClient.class);
        service = new EasyTranslatorService(provider, generator, limiter, ocrClient);
    }

    @Test
    void 개인정보를_가리고_보내고_결과를_돌려준다() {
        when(generator.generate(anyString(), anyString())).thenReturn(GOOD);
        EasyTextDto.Response r = service.translate("ip:1.2.3.4", TEXT);
        verify(generator).generate(anyString(), argThat(user -> !user.contains("010-0000-0000")
                && !user.contains("000101-4000000") && user.contains("[가림]")));
        assertEquals(EasyTranslatorService.DONE, r.status());
        assertTrue(r.masked());
        assertEquals(1, r.sections().size());
        assertEquals(EasyTextLimiter.DAILY_LIMIT - 1, r.remaining());
    }

    @Test
    void 검사에_걸리면_FAILED_안내() {
        when(generator.generate(anyString(), anyString())).thenReturn("{\"summary\":\"100만 원을 내요.\",\"sections\":[]}");
        EasyTextDto.Response r = service.translate("ip:1.2.3.4", "납부기한은 12월 31일입니다.");
        assertEquals(EasyTranslatorService.FAILED, r.status());
        assertEquals(EasyTranslatorService.FAILED_SUMMARY, r.summary());
        assertFalse(r.masked());
    }

    @Test
    void 빈_글이나_너무_긴_글은_INVALID_INPUT() {
        assertEquals("INVALID_INPUT", assertThrows(EasyTextException.class,
                () -> service.translate("ip:1", "  ")).getCode());
        assertEquals("INVALID_INPUT", assertThrows(EasyTextException.class,
                () -> service.translate("ip:1", "가".repeat(EasyTranslatorService.MAX_TEXT + 1))).getCode());
        verify(generator, never()).generate(anyString(), anyString());
    }

    @Test
    void 키가_없으면_AI_UNAVAILABLE이고_횟수를_쓰지_않는다() {
        when(provider.getIfAvailable()).thenReturn(null);
        assertEquals("AI_UNAVAILABLE", assertThrows(EasyTextException.class,
                () -> service.translate("ip:1", TEXT)).getCode());
        assertEquals(EasyTextLimiter.DAILY_LIMIT, limiter.remaining("ip:1"));
    }

    @Test
    void 호출이_실패하면_AI_UNAVAILABLE() {
        when(generator.generate(anyString(), anyString())).thenThrow(new RuntimeException("503"));
        assertEquals("AI_UNAVAILABLE", assertThrows(EasyTextException.class,
                () -> service.translate("ip:1", TEXT)).getCode());
    }

    @Test
    void 하루_한도를_넘으면_TOO_MANY_REQUESTS() {
        for (int i = 0; i < EasyTextLimiter.DAILY_LIMIT; i++) {
            limiter.tryUse("user:7");
        }
        assertEquals("TOO_MANY_REQUESTS", assertThrows(EasyTextException.class,
                () -> service.translate("user:7", TEXT)).getCode());
        verify(generator, never()).generate(anyString(), anyString());
    }

    @Test
    void 사진은_OCR로_읽은_글을_같은_길로_바꾼다() {
        MockMultipartFile file = new MockMultipartFile("file", "bill.png", "image/png", new byte[] {1, 2});
        when(ocrClient.text(org.mockito.ArgumentMatchers.any(), anyString()))
                .thenReturn(List.of("납부기한은 2026. 12. 31.까지입니다."));
        when(generator.generate(anyString(), anyString())).thenReturn(GOOD);
        EasyTextDto.Response r = service.translateFile("ip:1", file);
        assertEquals(EasyTranslatorService.DONE, r.status());
        verify(generator).generate(anyString(), argThat(user -> user.contains("납부기한은 2026. 12. 31.까지입니다.")));
    }

    @Test
    void AI가_꺼져_있으면_OCR도_안_돌린다() {
        when(provider.getIfAvailable()).thenReturn(null);
        MockMultipartFile file = new MockMultipartFile("file", "bill.png", "image/png", new byte[] {1});
        assertEquals("AI_UNAVAILABLE", assertThrows(EasyTextException.class,
                () -> service.translateFile("ip:1", file)).getCode());
        verify(ocrClient, never()).text(org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void 사진에서_글자를_못_찾으면_INVALID_INPUT() {
        MockMultipartFile file = new MockMultipartFile("file", "bill.png", "image/png", new byte[] {1});
        when(ocrClient.text(org.mockito.ArgumentMatchers.any(), anyString())).thenReturn(List.of());
        assertEquals("INVALID_INPUT", assertThrows(EasyTextException.class,
                () -> service.translateFile("ip:1", file)).getCode());
        verify(generator, never()).generate(anyString(), anyString());
    }
}
