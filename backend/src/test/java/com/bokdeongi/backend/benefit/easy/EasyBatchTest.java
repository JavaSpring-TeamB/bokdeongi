package com.bokdeongi.backend.benefit.easy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bokdeongi.backend.benefit.dto.response.EasyResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;

/** 쉬운 말 생성은 흉내만 낸다. 공고는 가짜 */
class EasyBatchTest {

    private static final LocalDateTime MODIFIED = LocalDateTime.of(2026, 11, 1, 4, 0);
    private static final EasyResponse DONE = new EasyResponse(EasyTextService.DONE, "쉬운 말",
            new EasyResponse.Summary("누가", "무엇을", "어떻게", null), MODIFIED, EasyResponse.NOTICE);

    private BenefitRepository repository;
    private EasyTextService service;
    private EasyBatch batch;

    @BeforeEach
    void setUp() {
        repository = mock(BenefitRepository.class);
        service = mock(EasyTextService.class);
        batch = new EasyBatch(repository, service);
    }

    private Benefit benefit(String serviceId, boolean cached) {
        Benefit b = mock(Benefit.class);
        when(b.getApiModifiedAt()).thenReturn(MODIFIED);
        if (cached) {
            when(b.getEasyText()).thenReturn("이미 만든 글");
            when(b.getAiGeneratedAt()).thenReturn(MODIFIED.plusDays(1));
            when(b.getAiSourceModifiedAt()).thenReturn(MODIFIED);
        }
        when(repository.findByServiceId(serviceId)).thenReturn(Optional.of(b));
        return b;
    }

    @Test
    void 없는_공고는_notFound_있는_공고는_새로_만들거나_그대로_쓴다() {
        Benefit fresh = benefit("A", false);
        Benefit cached = benefit("B", true);
        when(repository.findByServiceId("X")).thenReturn(Optional.empty());
        when(service.easy(fresh)).thenReturn(DONE);
        when(service.easy(cached)).thenReturn(DONE);

        EasyBatch.Report r = batch.run(List.of("A", " B ", "X", "A", ""));

        assertEquals(3, r.requested());
        assertEquals(List.of("X"), r.notFound());
        assertEquals(1, r.done());
        assertEquals(1, r.reused());
        assertEquals(0, r.failed());
    }

    @Test
    void 검사_탈락이나_호출_예외는_failed로_세고_다음_공고를_계속한다() {
        Benefit bad = benefit("A", false);
        Benefit boom = benefit("B", false);
        Benefit good = benefit("C", false);
        when(service.easy(bad)).thenReturn(EasyResponse.failed());
        when(service.easy(boom)).thenThrow(new RuntimeException("DB 연결 끊김"));
        when(service.easy(good)).thenReturn(DONE);

        EasyBatch.Report r = batch.run(List.of("A", "B", "C"));

        assertEquals(2, r.failed());
        assertEquals(List.of("A", "B"), r.failedIds());
        assertEquals(1, r.done());
    }

    @Test
    void 아이디가_비면_아무것도_안_부른다() {
        EasyBatch.Report r = batch.run(List.of(" ", ""));
        assertEquals(0, r.requested());
        verify(service, never()).easy(org.mockito.ArgumentMatchers.any());
    }
}
