package com.bokdeongi.backend.benefit.ask;

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

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;

/** LLM은 흉내만 낸다. 공고·질문은 가짜 (D-92: 테스트 질문만) */
class AskServiceTest {

    private BenefitRepository repository;
    private AskGenerator generator;
    private AskLimiter limiter;
    private AskService service;
    private Benefit benefit;

    @BeforeEach
    void setUp() {
        repository = mock(BenefitRepository.class);
        generator = mock(AskGenerator.class);
        limiter = new AskLimiter();
        service = new AskService(repository, generator, limiter);

        benefit = mock(Benefit.class);
        when(benefit.getTitle()).thenReturn("청년 월세 지원");
        when(benefit.getOrgName()).thenReturn("국토교통부");
        when(benefit.getTargetText()).thenReturn("부모와 따로 사는 만 19~34세 무주택 청년");
        when(benefit.getContentText()).thenReturn("월 최대 200,000원, 최대 12개월 지원");
        when(benefit.getContact()).thenReturn("주거복지과/1599-0001");
        when(repository.findById(7L)).thenReturn(Optional.of(benefit));
        when(generator.available()).thenReturn(true);
    }

    @Test
    void 원문에서_찾으면_근거와_함께_답한다() {
        when(generator.generate(anyString(), anyString())).thenReturn(
                "{\"found\":true,\"answer\":\"한 달에 최대 20만 원을 받아요.\",\"quotes\":[\"월 최대 200,000원, 최대 12개월 지원\"],\"eligibility\":false}");
        AskDto.Response r = service.ask(1L, 7L, "얼마나 받아요?");
        assertTrue(r.foundInSource());
        assertEquals("한 달에 최대 20만 원을 받아요.", r.answer());
        assertEquals(List.of("월 최대 200,000원, 최대 12개월 지원"), r.sourceQuotes());
        assertEquals(AskLimiter.DAILY_LIMIT - 1, r.remaining());
    }

    @Test
    void 원문에_없으면_문의처를_붙인다() {
        when(generator.generate(anyString(), anyString())).thenReturn(
                "{\"found\":false,\"answer\":\"공고에는 휴학생에 대한 내용이 없어요.\",\"quotes\":[],\"eligibility\":true}");
        AskDto.Response r = service.ask(1L, 7L, "휴학생도 받을 수 있어요?");
        assertFalse(r.foundInSource());
        assertEquals("공고에는 휴학생에 대한 내용이 없어요. 문의처(주거복지과 1599-0001)에 확인해 보세요.", r.answer());
        assertTrue(r.eligibility());
    }

    @Test
    void 검사에_걸리면_못_찾았다고_답한다() {
        when(generator.generate(anyString(), anyString())).thenReturn(
                "{\"found\":true,\"answer\":\"네, 받을 수 있어요.\",\"quotes\":[\"부모와 따로 사는 만 19~34세 무주택 청년\"],\"eligibility\":true}");
        AskDto.Response r = service.ask(1L, 7L, "저 받을 수 있어요?");
        assertFalse(r.foundInSource());
        assertEquals("공고에서 답을 찾지 못했어요. 문의처(주거복지과 1599-0001)에 확인해 보세요.", r.answer());
        assertTrue(r.eligibility());
    }

    @Test
    void 질문_속_개인정보는_가리고_보낸다() {
        when(generator.generate(anyString(), anyString())).thenReturn(
                "{\"found\":false,\"answer\":\"공고에는 그 내용이 없어요.\",\"quotes\":[],\"eligibility\":false}");
        service.ask(1L, 7L, "제 번호 010-0000-0000, 000101-4000000인데 받을 수 있어요?");
        verify(generator).generate(anyString(), argThat(user -> !user.contains("010-0000-0000")
                && !user.contains("000101-4000000") && user.contains("[가림]")));
    }

    @Test
    void 하루_30번을_넘으면_TOO_MANY_REQUESTS() {
        for (int i = 0; i < AskLimiter.DAILY_LIMIT; i++) {
            limiter.tryUse(1L);
        }
        assertEquals("TOO_MANY_REQUESTS", assertThrows(AskException.class,
                () -> service.ask(1L, 7L, "얼마나 받아요?")).getCode());
        verify(generator, never()).generate(anyString(), anyString());
    }

    @Test
    void 키가_없으면_AI_UNAVAILABLE이고_횟수를_쓰지_않는다() {
        when(generator.available()).thenReturn(false);
        assertEquals("AI_UNAVAILABLE", assertThrows(AskException.class,
                () -> service.ask(1L, 7L, "얼마나 받아요?")).getCode());
        assertEquals(AskLimiter.DAILY_LIMIT, limiter.remaining(1L));
    }

    @Test
    void 호출이_실패하면_AI_UNAVAILABLE() {
        when(generator.generate(anyString(), anyString())).thenThrow(new RuntimeException("503 overloaded"));
        assertEquals("AI_UNAVAILABLE", assertThrows(AskException.class,
                () -> service.ask(1L, 7L, "얼마나 받아요?")).getCode());
    }

    @Test
    void 질문이_비거나_길면_INVALID_INPUT_없는_혜택은_NOT_FOUND() {
        assertEquals("INVALID_INPUT", assertThrows(AskException.class, () -> service.ask(1L, 7L, " ")).getCode());
        assertEquals("INVALID_INPUT", assertThrows(AskException.class,
                () -> service.ask(1L, 7L, "가".repeat(AskService.MAX_QUESTION + 1))).getCode());
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertEquals("NOT_FOUND", assertThrows(AskException.class, () -> service.ask(1L, 99L, "얼마나 받아요?")).getCode());
    }

    @Test
    void 문의처가_없으면_소관기관으로() {
        when(benefit.getContact()).thenReturn(null);
        assertEquals("소관기관(국토교통부)에 확인해 보세요.", AskService.contactLine(benefit));
    }
}
