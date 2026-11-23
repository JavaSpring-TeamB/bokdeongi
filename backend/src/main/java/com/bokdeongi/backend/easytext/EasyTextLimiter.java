package com.bokdeongi.backend.easytext;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 쉬운 말 번역기 하루 횟수 제한. 로그인 없이도 쓰는 API라 로그인하면 회원 번호, 아니면 접속 주소로 센다.
 * 글은 저장하지 않으므로 횟수도 메모리에만 센다 (공고문 질문 AskLimiter와 같은 방식). 서버를 다시 켜면 0부터.
 */
@Component
public class EasyTextLimiter {

    public static final int DAILY_LIMIT = 20;

    private final Clock clock;
    private final Map<String, Count> counts = new ConcurrentHashMap<>();

    private record Count(LocalDate day, int used) {
    }

    @Autowired
    public EasyTextLimiter() {
        this(Clock.systemDefaultZone());
    }

    EasyTextLimiter(Clock clock) {
        this.clock = clock;
    }

    /** 한 번 쓴다. 오늘 한도를 넘으면 false (세지 않음) */
    public boolean tryUse(String key) {
        LocalDate today = LocalDate.now(clock);
        boolean[] ok = {false};
        counts.compute(key, (k, c) -> {
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
    public int remaining(String key) {
        Count c = counts.get(key);
        if (c == null || !c.day().equals(LocalDate.now(clock))) {
            return DAILY_LIMIT;
        }
        return Math.max(0, DAILY_LIMIT - c.used());
    }
}
