package com.bokdeongi.backend.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bokdeongi.backend.extraction.PeriodStatus.Label;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 신청 기간 분류 (D-85). 사례는 data-lab/api_survey/test_stale_check.py와 같다. */
class PeriodStatusTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private static Label label(String text) {
        return label(text, "직접입력");
    }

    private static Label label(String text, String type) {
        return PeriodStatus.classify(type, text, "", 2026, TODAY).label();
    }

    @Test
    @DisplayName("작년 이전에 끝남")
    void oldPeriod() {
        assertEquals(Label.PAST_OLD, label("2023.02.02~2023.04.28", "기간설정"));
        assertEquals(Label.PAST_OLD, label("2025년 하반기"));
        assertTrue(Label.PAST_OLD.expired());
    }

    @Test
    @DisplayName("올해 이미 끝남")
    void thisYearPassed() {
        assertEquals(Label.PAST_THIS, label("2026.04.27~2026.05.29", "기간설정"));
        assertEquals(Label.PAST_THIS, label("상반기 : 2025. 11. 12. ~ 11. 26. 하반기 : 2026. 5. 18. ~ 6. 2."));
        assertEquals(Label.PAST_THIS, label("2026년 상반기"));
    }

    @Test
    @DisplayName("진행 중·예정 (월까지만이면 그달 말일까지)")
    void open() {
        assertEquals(Label.OPEN, label("2026.10.15.~2027.4.30."));
        assertEquals(Label.OPEN, label("2026. 4.  ~ 2026.  11."));
        assertEquals(Label.OPEN, label("2026. 7월 말~ 8월 말, 2027년 2월~3월(예정)"));
        assertEquals(Label.OPEN, label("2026.1. ~ 12.(※예산 상황에 따라 변경 가능)"));
        assertFalse(Label.OPEN.expired());
    }

    @Test
    @DisplayName("반복·날짜로 판단 안 함")
    void recurringAndNoDate() {
        assertEquals(Label.RECURRING, label("매년 01-01~01-31"));
        assertEquals(Label.NO_DATE, label("상시신청", "상시신청"));
        assertEquals(Label.NO_DATE, label("신청불필요"));
        assertEquals(Label.NO_DATE, label("당해연도 예산 소진 시까지"));
        assertEquals(Label.NO_DATE, label("2월 부터 예산 소진시 까지"));
        assertEquals(Label.NO_DATE, label("급여의 사유가 발생한 날로부터 5년 이내(2012. 7. 1. 이후 ...)"));
        assertEquals(Label.NO_DATE, label("생식세포 채취일로부터 6개월 (2025.1.1. 을 포함하여 그 이후에)"));
    }

    @Test
    @DisplayName("지원대상 옛 연도는 보조 표시만")
    void staleTarget() {
        assertTrue(PeriodStatus.classify("상시신청", "상시신청", "CT/영상/디자인 전공자로서 2024년 2월 졸업예정자", 2026, TODAY)
                .staleText());
        assertFalse(PeriodStatus.classify("상시신청", "상시신청", "2026년 기준 만 19세 이상", 2026, TODAY).staleText());
    }
}
