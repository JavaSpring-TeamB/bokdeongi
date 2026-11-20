package com.bokdeongi.backend.search.service;

import com.bokdeongi.backend.benefit.entity.Benefit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BenefitSearchPeriodServiceTest {

    private final BenefitSearchPeriodService service =
            new BenefitSearchPeriodService();

    @Test
    void 종료된_연례반복공고는_다음_예상기간을_계산한다() {
        Benefit benefit = mock(Benefit.class);

        when(benefit.getRecurringYearly()).thenReturn(true);
        when(benefit.getApplyStart())
                .thenReturn(LocalDate.of(2026, 10, 4));
        when(benefit.getApplyEnd())
                .thenReturn(LocalDate.of(2026, 11, 18));

        var result = service.calculate(benefit);

        assertEquals(
                LocalDate.of(2027, 10, 4),
                result.expectedApplyStart()
        );
        assertEquals(
                LocalDate.of(2027, 11, 18),
                result.expectedApplyEnd()
        );
    }

    @Test
    void 일반공고는_예상기간을_계산하지_않는다() {
        Benefit benefit = mock(Benefit.class);

        when(benefit.getRecurringYearly()).thenReturn(false);

        var result = service.calculate(benefit);

        assertNull(result.expectedApplyStart());
        assertNull(result.expectedApplyEnd());
    }

    @Test
    void 신청기간이_남은_반복공고는_예상기간을_계산하지_않는다() {
        Benefit benefit = mock(Benefit.class);

        when(benefit.getRecurringYearly()).thenReturn(true);
        when(benefit.getApplyStart())
                .thenReturn(LocalDate.now().minusDays(5));
        when(benefit.getApplyEnd())
                .thenReturn(LocalDate.now().plusDays(5));

        var result = service.calculate(benefit);

        assertNull(result.expectedApplyStart());
        assertNull(result.expectedApplyEnd());
    }

    @Test
    void 신청기간이_없으면_예상기간을_계산하지_않는다() {
        Benefit benefit = mock(Benefit.class);

        when(benefit.getRecurringYearly()).thenReturn(true);
        when(benefit.getApplyStart()).thenReturn(null);

        var result = service.calculate(benefit);

        assertNull(result.expectedApplyStart());
        assertNull(result.expectedApplyEnd());
    }
}