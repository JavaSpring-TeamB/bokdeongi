package com.bokdeongi.backend.benefit.easy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.bokdeongi.backend.benefit.dto.response.EasyResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;

/** LLM은 흉내만 낸다. 공고는 가짜 */
class EasyTextServiceTest {

    private static final LocalDateTime API_MODIFIED = LocalDateTime.of(2026, 11, 1, 4, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 16, 0);
    private static final String GOOD = "{\"who\":\"만 19~34세 청년\",\"what\":\"월 최대 20만 원\",\"how\":\"복지로 온라인 신청\","
            + "\"when\":null,\"text\":\"청년 월세를 돕는 지원이에요.\"}";

    private EasyGenerator generator;
    private ObjectProvider<EasyGenerator> provider;
    private BenefitRepository repository;
    private EasyTextService service;
    private Benefit benefit;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        generator = mock(EasyGenerator.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(generator);
        repository = mock(BenefitRepository.class);
        Clock clock = Clock.fixed(ZonedDateTime.of(NOW, ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        service = new EasyTextService(provider, repository, clock);

        benefit = mock(Benefit.class);
        when(benefit.getTitle()).thenReturn("청년 월세 지원");
        when(benefit.getTargetText()).thenReturn("만 19~34세 청년");
        when(benefit.getContentText()).thenReturn("월 최대 200,000원");
        when(benefit.getApplyMethodText()).thenReturn("복지로 온라인 신청");
        when(benefit.getApiModifiedAt()).thenReturn(API_MODIFIED);
    }

    @Test
    void 저장된_글이_있고_공고가_그대로면_LLM을_부르지_않는다() {
        when(benefit.getAiStatus()).thenReturn("DONE");
        when(benefit.getEasyText()).thenReturn("저장된 쉬운 말");
        when(benefit.getAiGeneratedAt()).thenReturn(NOW.minusDays(1));
        when(benefit.getAiSourceModifiedAt()).thenReturn(API_MODIFIED);
        EasyResponse r = service.easy(benefit);
        assertEquals("DONE", r.status());
        assertEquals("저장된 쉬운 말", r.text());
        verify(generator, never()).generate(anyString(), anyString());
    }

    @Test
    void 공고가_바뀌었으면_다시_만든다() {
        when(benefit.getAiStatus()).thenReturn("DONE");
        when(benefit.getEasyText()).thenReturn("옛 글");
        when(benefit.getAiGeneratedAt()).thenReturn(NOW.minusDays(30));
        when(benefit.getAiSourceModifiedAt()).thenReturn(API_MODIFIED.minusDays(30));
        when(generator.generate(anyString(), anyString())).thenReturn(GOOD);
        service.easy(benefit);
        verify(benefit).applyEasy(eq("DONE"), eq("만 19~34세 청년"), eq("월 최대 20만 원"), eq("복지로 온라인 신청"), isNull(),
                eq("청년 월세를 돕는 지원이에요."), eq(NOW));
        verify(repository).save(benefit);
    }

    @Test
    void 검사에_걸리면_FAILED로_저장() {
        when(generator.generate(anyString(), anyString())).thenReturn(
                "{\"who\":\"청년\",\"what\":\"1년에 240만 원\",\"how\":\"복지로\",\"when\":null,\"text\":\"월세 지원이에요.\"}");
        EasyResponse r = service.easy(benefit);
        assertEquals("FAILED", r.status());
        verify(benefit).applyEasy(eq("FAILED"), isNull(), isNull(), isNull(), isNull(), isNull(), eq(NOW));
    }

    @Test
    void 같은_원문으로_실패했으면_다시_부르지_않는다() {
        when(benefit.getAiStatus()).thenReturn("FAILED");
        when(benefit.getAiGeneratedAt()).thenReturn(NOW.minusHours(1));
        when(benefit.getAiSourceModifiedAt()).thenReturn(API_MODIFIED);
        assertEquals("FAILED", service.easy(benefit).status());
        verify(generator, never()).generate(anyString(), anyString());
    }

    @Test
    void 호출이_실패하면_저장하지_않는다() {
        when(generator.generate(anyString(), anyString())).thenThrow(new RuntimeException("503 overloaded"));
        assertEquals("FAILED", service.easy(benefit).status());
        verify(repository, never()).save(any());
    }

    @Test
    void LLM_구현이_없으면_FAILED() {
        when(provider.getIfAvailable()).thenReturn(null);
        assertEquals("FAILED", service.easy(benefit).status());
        verify(repository, never()).save(any());
    }

    @Test
    void 동기화가_상태를_PENDING으로_돌려도_수정일시가_같으면_다시_부르지_않는다() {
        when(benefit.getAiStatus()).thenReturn("PENDING");
        when(benefit.getEasyText()).thenReturn("저장된 쉬운 말");
        when(benefit.getAiGeneratedAt()).thenReturn(NOW.minusDays(1));
        when(benefit.getAiSourceModifiedAt()).thenReturn(API_MODIFIED);
        assertEquals("저장된 쉬운 말", service.easy(benefit).text());
        verify(generator, never()).generate(anyString(), anyString());
    }

    @Test
    void 처음_만든_뒤에는_같은_공고로_다시_부르지_않는다() {
        // 처음: 저장된 글 없음 → 한 번 부르고 저장
        when(generator.generate(anyString(), anyString())).thenReturn(GOOD);
        service.easy(benefit);
        // 저장된 뒤 상태를 흉내 내서 두 번 더 조회
        when(benefit.getAiStatus()).thenReturn("DONE");
        when(benefit.getEasyText()).thenReturn("청년 월세를 돕는 지원이에요.");
        when(benefit.getAiGeneratedAt()).thenReturn(NOW);
        when(benefit.getAiSourceModifiedAt()).thenReturn(API_MODIFIED);
        service.easy(benefit);
        service.easy(benefit);
        verify(generator, times(1)).generate(anyString(), anyString());
    }
}
