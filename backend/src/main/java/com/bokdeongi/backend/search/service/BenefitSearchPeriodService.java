package com.bokdeongi.backend.search.service;

import com.bokdeongi.backend.benefit.entity.Benefit;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class BenefitSearchPeriodService {

    public ExpectedPeriod calculate(Benefit benefit) {

        if (!Boolean.TRUE.equals(benefit.getRecurringYearly())) {
            return new ExpectedPeriod(null, null);
        }

        LocalDate applyStart = benefit.getApplyStart();
        LocalDate applyEnd = benefit.getApplyEnd();

        if (applyStart == null || applyEnd == null) {
            return new ExpectedPeriod(null, null);
        }

        LocalDate today = LocalDate.now();

        if (!applyEnd.isBefore(today)) {
            return new ExpectedPeriod(null, null);
        }

        LocalDate expectedStart = applyStart;
        LocalDate expectedEnd = applyEnd;

        while (expectedEnd.isBefore(today)) {
            expectedStart = expectedStart.plusYears(1);
            expectedEnd = expectedEnd.plusYears(1);
        }

        return new ExpectedPeriod(expectedStart, expectedEnd);
    }

    public record ExpectedPeriod(
            LocalDate expectedApplyStart,
            LocalDate expectedApplyEnd
    ) {
    }
}