package com.bokdeongi.backend.easytext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class EasyTextLimiterTest {

    /** 시각을 바꿀 수 있는 시계 */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-25T01:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void 한도를_넘으면_false() {
        EasyTextLimiter limiter = new EasyTextLimiter(new MovableClock());
        for (int i = 0; i < EasyTextLimiter.DAILY_LIMIT; i++) {
            assertTrue(limiter.tryUse("user:1"));
        }
        assertFalse(limiter.tryUse("user:1"));
        assertEquals(0, limiter.remaining("user:1"));
    }

    @Test
    void 돌려주면_한_번_더_쓸_수_있다() {
        EasyTextLimiter limiter = new EasyTextLimiter(new MovableClock());
        limiter.tryUse("user:1");
        limiter.refund("user:1");
        assertEquals(EasyTextLimiter.DAILY_LIMIT, limiter.remaining("user:1"));
        limiter.refund("user:1"); // 0 아래로는 안 내려간다
        assertEquals(EasyTextLimiter.DAILY_LIMIT, limiter.remaining("user:1"));
    }

    @Test
    void 날짜가_바뀌면_지난_기록을_지운다() {
        MovableClock clock = new MovableClock();
        EasyTextLimiter limiter = new EasyTextLimiter(clock);
        limiter.tryUse("user:1");
        limiter.tryUse("user:2");
        assertEquals(2, limiter.size());
        clock.now = clock.now.plusSeconds(24 * 60 * 60);
        limiter.tryUse("user:3");
        assertEquals(1, limiter.size());
    }
}
