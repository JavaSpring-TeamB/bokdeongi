package com.bokdeongi.backend.eligibility.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 프로필 저장이 커밋된 뒤 다른 스레드에서 재판정한다. 혜택이 많아 판정이 오래 걸려도 저장 응답을 막지 않는다.
 * 판정 중 목록 조회는 이전 결과를 보여 준다 (BEN-01 예외: '업데이트 중').
 */
@Component
public class ProfileChangedListener {

    private final EligibilityService service;

    public ProfileChangedListener(EligibilityService service) {
        this.service = service;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProfileChanged(ProfileChangedEvent event) {
        service.judgeUser(event.userId());
    }
}
