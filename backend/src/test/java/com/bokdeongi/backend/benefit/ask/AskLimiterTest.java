package com.bokdeongi.backend.benefit.ask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class AskLimiterTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static class MovableClock extends Clock {
        Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return SEOUL;
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
    void 하루_30번까지_되고_31번째는_막는다() {
        AskLimiter limiter = new AskLimiter(Clock.fixed(Instant.parse("2026-11-19T03:00:00Z"), SEOUL));
        for (int i = 0; i < AskLimiter.DAILY_LIMIT; i++) {
            assertTrue(limiter.tryUse(1L));
        }
        assertFalse(limiter.tryUse(1L));
        assertEquals(0, limiter.remaining(1L));
        assertTrue(limiter.tryUse(2L)); // 다른 사람은 따로 센다
        assertEquals(AskLimiter.DAILY_LIMIT - 1, limiter.remaining(2L));
    }

    @Test
    void 날짜가_바뀌면_다시_30번() {
        MovableClock clock = new MovableClock(Instant.parse("2026-11-19T14:00:00Z")); // 서울 23시
        AskLimiter limiter = new AskLimiter(clock);
        for (int i = 0; i < AskLimiter.DAILY_LIMIT; i++) {
            limiter.tryUse(1L);
        }
        assertFalse(limiter.tryUse(1L));
        clock.now = Instant.parse("2026-11-19T15:30:00Z").atOffset(ZoneOffset.UTC).toInstant(); // 서울 다음 날 0시 30분
        assertTrue(limiter.tryUse(1L));
        assertEquals(AskLimiter.DAILY_LIMIT - 1, limiter.remaining(1L));
    }
}
