package com.bokdeongi.backend.benefit.easy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import com.bokdeongi.backend.benefit.dto.response.EasyResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;

/** 쉬운 말 생성은 흉내만 낸다. 공고는 가짜 */
class EasyOnViewTest {

    private static final LocalDateTime MODIFIED = LocalDateTime.of(2026, 11, 1, 4, 0);
    private static final EasyResponse DONE = new EasyResponse("DONE", "쉬운 말", null, MODIFIED, EasyResponse.NOTICE);

    private EasyTextService service;
    private ObjectProvider<ChatModel> provider;
    private EasyOnView onView;
    private Benefit benefit;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = mock(EasyTextService.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(ChatModel.class));
        onView = new EasyOnView(service, provider);
        benefit = mock(Benefit.class);
        when(benefit.getApiModifiedAt()).thenReturn(MODIFIED);
        when(service.easy(benefit)).thenReturn(DONE);
    }

    @Test
    void 저장된_글이_없으면_처음_볼_때_만든다() {
        assertEquals("DONE", onView.view(benefit).status());
        verify(service).easy(benefit);
    }

    @Test
    void 저장된_글이_있으면_AI가_꺼져_있어도_보여_준다() {
        when(benefit.getAiGeneratedAt()).thenReturn(MODIFIED.plusDays(1));
        when(benefit.getAiSourceModifiedAt()).thenReturn(MODIFIED);
        when(benefit.getEasyText()).thenReturn("이미 만든 글");
        when(provider.getIfAvailable()).thenReturn(null);
        assertEquals("DONE", onView.view(benefit).status());
    }

    @Test
    void AI가_꺼져_있고_저장된_글이_없으면_NOT_READY() {
        when(provider.getIfAvailable()).thenReturn(null);
        assertEquals("NOT_READY", onView.view(benefit).status());
        verify(service, never()).easy(benefit);
    }

    @Test
    void 원문이_바뀌었으면_다시_만든다() {
        when(benefit.getAiGeneratedAt()).thenReturn(MODIFIED.minusDays(10));
        when(benefit.getAiSourceModifiedAt()).thenReturn(MODIFIED.minusDays(20));
        when(benefit.getEasyText()).thenReturn("옛 원문으로 만든 글");
        onView.view(benefit);
        verify(service).easy(benefit);
    }

    @Test
    void 하루_한도를_넘으면_NOT_READY() {
        for (int i = 0; i < EasyOnView.DAILY_LIMIT; i++) {
            onView.view(benefit);
        }
        assertEquals("NOT_READY", onView.view(benefit).status());
        verify(service, times(EasyOnView.DAILY_LIMIT)).easy(benefit);
    }
}
