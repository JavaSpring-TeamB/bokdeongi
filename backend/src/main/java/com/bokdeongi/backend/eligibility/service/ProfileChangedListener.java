package com.bokdeongi.backend.eligibility.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 프로필 저장이 커밋된 뒤 다른 스레드에서 재판정한다. 혜택이 많아 판정이 오래 걸려도 저장 응답을 막지 않는다.
 * 판정 중 목록 조회는 이전 결과를 보여 준다 (BEN-01 예외: '업데이트 중').
 *
 * <p>한 회원의 재판정은 한 번에 하나만 돈다. 프로필 1~4단계를 연달아 저장하면 이벤트가 겹치는데, 동시에 돌면 같은
 * 결과 줄을 서로 INSERT해 한쪽이 실패하고(eligibility_results 유일 키) 일만 여러 번 하게 된다. 판정 중에 들어온
 * 요청은 표시만 해 두고, 지금 판정이 끝나면 최신 프로필로 한 번 더 돈다 (요청이 몇 개든 최대 한 번).
 */
@Component
public class ProfileChangedListener {

    private static final Logger log = LoggerFactory.getLogger(ProfileChangedListener.class);

    private final EligibilityService service;
    /** 판정 중인 회원 → 판정 중에 새 요청이 들어왔는지 */
    private final ConcurrentMap<Long, AtomicBoolean> running = new ConcurrentHashMap<>();

    public ProfileChangedListener(EligibilityService service) {
        this.service = service;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProfileChanged(ProfileChangedEvent event) {
        rejudge(event.userId());
    }

    /** 이미 판정 중이면 다시 돌 표시만 하고 돌아간다. judgeUser는 트랜잭션이라 커밋된 뒤에 다음 판정이 시작된다 */
    void rejudge(long userId) {
        AtomicBoolean pending = new AtomicBoolean(true);
        AtomicBoolean current = running.putIfAbsent(userId, pending);
        if (current != null) {
            current.set(true);
            return;
        }
        try {
            do {
                while (pending.getAndSet(false)) {
                    try {
                        service.judgeUser(userId);
                    } catch (RuntimeException e) {
                        log.warn("회원 {} 재판정 실패: {}", userId, e.getMessage());
                    }
                }
                running.remove(userId, pending);
                // 마지막 판정과 remove 사이에 들어온 요청이 표시만 하고 돌아갔을 수 있다
            } while (pending.get() && running.putIfAbsent(userId, pending) == null);
        } finally {
            running.remove(userId, pending);
        }
    }
}
