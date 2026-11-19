package com.bokdeongi.backend.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChangeDetectorTest {

    private static final BenefitMapper MAPPER = new BenefitMapper(
            new OrgRegionResolver(List.of(), SyncFixtures.REGIONS), List.of(), Set.of(), LocalDate.of(2026, 11, 18));

    private static ServiceRecord rec() {
        ServiceRecord r = SyncFixtures.record("S1", "청년 월세 지원", "중앙행정기관", "국토교통부", "1613000",
                "20261101090000");
        r.detail().put("신청기한유형", "기간설정");
        r.detail().put("신청기한", "2026.11.1.~2026.12.10.");
        return r;
    }

    /** 지금 원문을 그대로 저장돼 있던 값으로 만든다 */
    private static ChangeDetector.Before before(BenefitRow r) {
        return new ChangeDetector.Before(r.title(), r.applyPeriodText(), r.applyStart(), r.applyEnd(), r.dateStatus(),
                r.targetText(), r.criteriaText(), r.maxAmount(), r.amountPeriod(), r.contentText(),
                r.requiredDocsText(), r.applyMethodText(), r.onlineApplyUrl());
    }

    @Test
    void 의미_있는_칸이_그대로면_변경_없음() {
        BenefitRow row = MAPPER.map(rec());
        ServiceRecord r = rec();
        r.list().put("조회수", "999");
        r.detail().put("지원대상", "관내 거주   청년\n"); // 띄어쓰기만 다름
        ChangeDetector.Result result = ChangeDetector.diff(before(row), MAPPER.map(r));
        assertFalse(result.changed());
        assertNull(result.summary());
    }

    @Test
    void 마감일이_바뀌면_신청기한_변경이_먼저() {
        BenefitRow old = MAPPER.map(rec());
        ServiceRecord r = rec();
        r.detail().put("신청기한", "2026.11.1.~2026.12.31.");
        r.detail().put("구비서류", "주민등록등본");
        ChangeDetector.Result result = ChangeDetector.diff(before(old), MAPPER.map(r));
        assertEquals("신청기한이 변경됐어요 (외 1건)", result.summary());
        ChangeDetector.Change period = result.changes().get(0);
        assertEquals("APPLY_PERIOD", period.field());
        assertEquals("2026-11-01 ~ 2026-12-10", period.before());
        assertEquals("2026-11-01 ~ 2026-12-31", period.after());
    }

    @Test
    void 금액이_바뀌면_금액_변경_지원내용_글은_따로_세지_않는다() {
        BenefitRow old = MAPPER.map(rec());
        ServiceRecord r = rec();
        r.detail().put("지원내용", "월 최대 30만원 지원");
        ChangeDetector.Result result = ChangeDetector.diff(before(old), MAPPER.map(r));
        assertEquals("지원 금액이 변경됐어요", result.summary());
        assertEquals("월 200,000원", result.changes().get(0).before());
        assertEquals("월 300,000원", result.changes().get(0).after());
    }

    @Test
    void 대상이_바뀌면_대상_조건_변경() {
        BenefitRow old = MAPPER.map(rec());
        ServiceRecord r = rec();
        r.detail().put("지원대상", "관내 거주 청년 (무주택자)");
        ChangeDetector.Result result = ChangeDetector.diff(before(old), MAPPER.map(r));
        assertEquals("지원 대상·조건이 변경됐어요", result.summary());
        assertTrue(result.changes().get(0).after().contains("무주택자"));
    }

    @Test
    void 긴_글은_앞_200자만() {
        String longText = "가".repeat(500);
        assertEquals(201, ChangeDetector.cut(longText).length());
    }
}
