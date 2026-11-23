package com.bokdeongi.backend.eligibility.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ProfileChangedListenerTest {

    /** 첫 판정을 붙잡아 두고, 동시에 몇 개가 도는지 센다 */
    static class SlowService extends EligibilityService {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger concurrent = new AtomicInteger();
        final AtomicInteger maxConcurrent = new AtomicInteger();
        volatile boolean failFirst;

        SlowService() {
            super(null, null);
        }

        @Override
        public int judgeUser(long userId) {
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            try {
                if (calls.incrementAndGet() == 1) {
                    started.countDown();
                    release.await(5, TimeUnit.SECONDS);
                    if (failFirst) {
                        throw new IllegalStateException("DB 오류");
                    }
                }
                return 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0;
            } finally {
                concurrent.decrementAndGet();
            }
        }
    }

    private static Thread start(ProfileChangedListener l, long userId) {
        Thread t = new Thread(() -> l.rejudge(userId));
        t.start();
        return t;
    }

    @Test
    void 판정_중에_들어온_요청은_끝난_뒤_한_번만_더_돈다() throws Exception {
        SlowService s = new SlowService();
        ProfileChangedListener l = new ProfileChangedListener(s);
        Thread first = start(l, 1L);
        assertTrue(s.started.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < 3; i++) {
            l.rejudge(1L);   // 판정 중이라 표시만 하고 바로 돌아온다
        }
        assertEquals(1, s.calls.get());
        s.release.countDown();
        first.join(5000);
        assertEquals(2, s.calls.get());
        assertEquals(1, s.maxConcurrent.get());
    }

    @Test
    void 다른_회원은_같이_돈다() throws Exception {
        SlowService s = new SlowService();
        ProfileChangedListener l = new ProfileChangedListener(s);
        Thread first = start(l, 1L);
        assertTrue(s.started.await(5, TimeUnit.SECONDS));
        l.rejudge(2L);
        assertEquals(2, s.calls.get());
        s.release.countDown();
        first.join(5000);
        assertEquals(2, s.calls.get());
    }

    @Test
    void 판정이_실패해도_다음_요청은_돈다() throws Exception {
        SlowService s = new SlowService();
        s.failFirst = true;
        ProfileChangedListener l = new ProfileChangedListener(s);
        Thread first = start(l, 1L);
        assertTrue(s.started.await(5, TimeUnit.SECONDS));
        l.rejudge(1L);
        s.release.countDown();
        first.join(5000);
        assertEquals(2, s.calls.get());
        l.rejudge(1L);   // 끝난 뒤의 새 요청도 막히지 않는다
        assertEquals(3, s.calls.get());
    }
}
