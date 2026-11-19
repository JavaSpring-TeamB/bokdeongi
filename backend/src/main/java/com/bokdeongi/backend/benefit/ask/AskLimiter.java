package com.bokdeongi.backend.benefit.ask;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 공고문 질문 1인 하루 30회 제한 (BN-08, TOO_MANY_REQUESTS).
 *
 * <p>질문·답변은 저장하지 않으므로 횟수도 메모리에만 센다. 서버를 다시 켜면 0부터.
 * 서버가 한 대라 이걸로 충분하고, 여러 대가 되면 DB나 Redis로 옮긴다.
 */
@Component
public class AskLimiter {

    public static final int DAILY_LIMIT = 30;

    private final Clock clock;
    private final Map<Long, Count> counts = new ConcurrentHashMap<>();

    private record Count(LocalDate day, int used) {
    }

    @Autowired
    public AskLimiter() {
        this(Clock.systemDefaultZone());
    }

    AskLimiter(Clock clock) {
        this.clock = clock;
    }

    /** 한 번 쓴다. 오늘 한도를 넘으면 false (세지 않음) */
    public boolean tryUse(long userId) {
        LocalDate today = LocalDate.now(clock);
        boolean[] ok = {false};
        counts.compute(userId, (id, c) -> {
            Count cur = c == null || !c.day().equals(today) ? new Count(today, 0) : c;
            if (cur.used() >= DAILY_LIMIT) {
                return cur;
            }
            ok[0] = true;
            return new Count(today, cur.used() + 1);
        });
        return ok[0];
    }

    /** 오늘 남은 횟수 */
    public int remaining(long userId) {
        Count c = counts.get(userId);
        if (c == null || !c.day().equals(LocalDate.now(clock))) {
            return DAILY_LIMIT;
        }
        return Math.max(0, DAILY_LIMIT - c.used());
    }
}
