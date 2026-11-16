package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.bokdeongi.backend.benefit.service.ApplyPeriodExtractor.Period;
import com.bokdeongi.backend.benefit.service.ApplyPeriodExtractor.Precision;
import com.bokdeongi.backend.benefit.service.ApplyPeriodExtractor.Result;
import com.bokdeongi.backend.benefit.service.ApplyPeriodExtractor.Status;

/** 날짜 기준 v2 */
class ApplyPeriodExtractorTest {

    private static final int POSTED = 2026;

    private static Result direct(String text) {
        return ApplyPeriodExtractor.extract("직접입력", text, POSTED);
    }

    private static void assertParsed(Result r, Precision precision, List<String> starts, List<String> ends) {
        assertEquals(Status.PARSED, r.status(), r.rule());
        assertEquals(precision, r.precision());
        assertEquals(starts, r.starts());
        assertEquals(ends, r.ends());
    }

    private static void assertCheck(String text) {
        Result r = direct(text);
        assertEquals(Status.CHECK_NOTICE, r.status(), text + " / " + r.rule());
        assertTrue(r.starts().isEmpty(), text);
    }

    @Test
    void API_유형으로_바로_정한다() {
        assertEquals(Status.ALWAYS, ApplyPeriodExtractor.extract("상시신청", "상시신청", POSTED).status());
        assertEquals(Status.VARIES, ApplyPeriodExtractor.extract("접수기관별상이", "접수기관 별 상이", POSTED).status());
        assertParsed(ApplyPeriodExtractor.extract("기간설정", "2026.04.27~2026.05.29", POSTED),
                Precision.DAY, List.of("2026-04-27"), List.of("2026-05-29"));
    }

    @Test
    void 연도_있는_날짜_범위() {
        assertParsed(direct("2026.10.15.~2027.4.30."), Precision.DAY, List.of("2026-10-15"), List.of("2027-04-30"));
        assertParsed(direct("2026. 7. 15.(수) 10:00 ~ 7. 28.(화) 24:00"),
                Precision.DAY, List.of("2026-07-15"), List.of("2026-07-28"));
        assertParsed(direct("2026. 2. 24~3. 10"), Precision.DAY, List.of("2026-02-24"), List.of("2026-03-10"));
        assertParsed(direct("상반기 : 2025. 11. 12. ~ 11. 26. 하반기 : 2026. 5. 18. ~ 6. 2."),
                Precision.DAY, List.of("2025-11-12", "2026-05-18"), List.of("2025-11-26", "2026-06-02"));
    }

    @Test
    void 기준1_연도와_월만_있으면_MONTH() {
        assertParsed(direct("2026. 4.  ~ 2026.  11."), Precision.MONTH, List.of("2026-04"), List.of("2026-11"));
        assertParsed(direct("2026. 4월 ~ 11월"), Precision.MONTH, List.of("2026-04"), List.of("2026-11"));
        assertParsed(direct("2026년 7월"), Precision.MONTH, List.of("2026-07"), List.of("2026-07"));
    }

    @Test
    void 기준1_기간_기준이_겹치면_CHECK() {
        assertCheck("2026. 8. 3.(월) ~ 9. 4.(금) ※단, 지정목표 개소에 미달하는 경우 연중 수시 모집");
        assertCheck("2026.1.19. ~ 2026.1.30.(추가모집 별도 진행 *문의 요망)");
        assertCheck("1~2차 집중신청 기간 운영후에 4월 부터 (초: 11월 말까지 / 중고: 10월 말까지) 상시 신청 운영");
        assertCheck("지방산림청(국유림관리소)별로 모집(2~3월)(2024년 미운영)");
    }

    @Test
    void 기준2_연도_없으면_게시_연도를_붙인다() {
        assertParsed(direct("매년 1월~3월"), Precision.MONTH, List.of("2026-01"), List.of("2026-03"));
        assertParsed(direct("매년 01-01~01-31"), Precision.DAY, List.of("2026-01-01"), List.of("2026-01-31"));
        assertParsed(direct("10월~12월경 독감예방접종 기간 중"), Precision.MONTH, List.of("2026-10"), List.of("2026-12"));
        assertParsed(direct("상반기(3~5월 중), 하반기(9~11월 중)"),
                Precision.MONTH, List.of("2026-03", "2026-09"), List.of("2026-05", "2026-11"));
        assertParsed(direct("매년 4월 말까지"), Precision.MONTH, List.of(""), List.of("2026-04"));
    }

    @Test
    void 기준2_게시_연도를_모르면_LLM으로() {
        Result r = ApplyPeriodExtractor.extract("직접입력", "매년 1월~3월", null);
        assertEquals(Status.CHECK_NOTICE, r.status());
        assertTrue(r.needsLlm());
    }

    @Test
    void 기준3_예산_소진과_상대_기간은_CHECK() {
        assertCheck("2026. 1.~2026. 12.(예산 소진 시까지)");
        assertCheck("당해연도 예산 소진 시까지");
        assertCheck("2월 부터 예산 소진시 까지");
        assertCheck("상시신청(신청기간 : 자녀의 출생일로부터 3개월 이내)");
        assertCheck("임신중이거나 출산 후 6개월 이내");
    }

    @Test
    void 기준4_연초는_PARSED_예정은_CHECK() {
        Result r = direct("연초(당해년도 1월)");
        assertParsed(r, Precision.MONTH, List.of("2026-01"), List.of("2026-01"));
        assertTrue(r.recurringYearly());

        assertCheck("매년 1월(예정)");
        assertCheck("2025년 2~4월중 신청예정");
        assertCheck("2026. 7월 말~ 8월 말, 2027년 2월~3월(예정)");
    }

    @Test
    void 기준5_다른_해_기준이면_CHECK() {
        assertCheck("사업시행 전년도 11월~12월");
        assertCheck("해당연도 사업은 전년도 3월까지 신청");
        assertCheck("진료받은 해당연도 12월 31일까지");
    }

    @Test
    void 기준6_그_밖의_예외는_CHECK() {
        assertCheck("무인민원발급기 운영시간");
        assertCheck("매 학기 시작 전 신청");
        assertCheck("상반기");          // 연도 없는 반기
        assertCheck("매년 상반기");
        assertCheck("2026년 상반기 예정"); // 예정은 CHECK
    }

    @Test
    void 연도_있는_반기는_첫달부터_끝달까지_MONTH() {
        assertParsed(direct("2026년 상반기"), Precision.MONTH, List.of("2026-01"), List.of("2026-06"));
        assertParsed(direct("2026년 하반기 중"), Precision.MONTH, List.of("2026-07"), List.of("2026-12"));
    }

    @Test
    void 신청불필요는_NO_APPLICATION_상시는_ALWAYS() {
        assertEquals(Status.NO_APPLICATION, direct("신청불필요").status());
        assertEquals(Status.NO_APPLICATION, direct("별도 신청절차 없음(설/추석 명절 지원)").status());
        assertEquals(Status.NO_APPLICATION, direct("별도신청 없음").status());
        assertEquals(Status.ALWAYS, direct("상시 신청").status());
    }

    @Test
    void 직접입력의_기관별_차이는_VARIES가_아니라_CHECK() {
        assertCheck("자세한 날짜는 지역농협에 따라 다를 수 있음");
        assertCheck("분기별 상이");
    }

    @Test
    void 연초는_월_숫자가_있을_때만_PARSED() {
        assertCheck("매년 연초");
        assertCheck("연초");
    }

    @Test
    void 매년이_있을_때만_반복() {
        assertTrue(direct("매년 1월~3월").recurringYearly());
        assertFalse(direct("10월~12월경 독감예방접종 기간 중").recurringYearly());
    }

    @Test
    void 없는_날짜는_버린다() {
        Result r = direct("2026.03.00.3.0.");
        assertEquals(Status.CHECK_NOTICE, r.status());
        assertTrue(r.needsLlm());
    }

    @Test
    void 기간이_여러개면_진행중인_기간을_저장한다() {
        Result r = direct("상반기(3~5월 중), 하반기(9~11월 중)");
        assertEquals(Optional.of(new Period("2026-09", "2026-11")),
                ApplyPeriodExtractor.pickPeriod(r, LocalDate.of(2026, 11, 13)));
    }

    @Test
    void 진행중이_없으면_다음_기간_그것도_없으면_최근_기간() {
        Result r = direct("상반기 : 2025. 11. 12. ~ 11. 26. 하반기 : 2026. 5. 18. ~ 6. 2.");
        assertEquals(Optional.of(new Period("2026-05-18", "2026-06-02")),
                ApplyPeriodExtractor.pickPeriod(r, LocalDate.of(2026, 1, 10)));
        assertEquals(Optional.of(new Period("2026-05-18", "2026-06-02")),
                ApplyPeriodExtractor.pickPeriod(r, LocalDate.of(2026, 11, 13)));
        assertEquals(Optional.of(new Period("2025-11-12", "2025-11-26")),
                ApplyPeriodExtractor.pickPeriod(r, LocalDate.of(2025, 11, 20)));
    }

    @Test
    void 마감만_있으면_그_기간을_저장한다() {
        Result r = direct("매년 4월 말까지");
        assertEquals(Optional.of(new Period("", "2026-04")), ApplyPeriodExtractor.pickPeriod(r, LocalDate.of(2026, 3, 1)));
    }

    @Test
    void 날짜가_없으면_저장할_기간도_없다() {
        assertEquals(Optional.empty(), ApplyPeriodExtractor.pickPeriod(direct("상시 신청"), LocalDate.of(2026, 11, 13)));
    }
}
