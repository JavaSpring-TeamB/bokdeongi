package com.bokdeongi.backend.eligibility.service;

import com.bokdeongi.backend.eligibility.EligibilityJudge;
import com.bokdeongi.backend.eligibility.store.EligibilityStore;
import com.bokdeongi.backend.eligibility.store.ProfileRow;
import com.bokdeongi.backend.eligibility.store.ResultRow;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 한 명의 혜택 전체를 판정해 eligibility_results에 저장한다 (ELIG-01, PROF-05 재매칭).
 *
 * <p>보통은 프로필이 저장된 뒤 {@link ProfileChangedEvent}로 비동기 호출된다. 이전 결과는 previous_result로,
 * 처음 해당된 시각은 first_eligible_at으로 이어받는다.
 */
@Service
public class EligibilityService {

    /** 신청 마감·나이 계산은 한국 날짜 기준 */
    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final EligibilityStore store;
    private final EligibilityBatch batch;
    private final Clock clock;

    // 생성자가 하나여야 스프링이 자동 주입한다. 판정 계산은 EligibilityBatch 단위 테스트로 검증한다
    public EligibilityService(EligibilityStore store) {
        this.store = store;
        this.batch = new EligibilityBatch(new EligibilityJudge());
        this.clock = Clock.systemUTC();
    }

    /**
     * @return 저장한 결과 수. 회원이 없으면 0
     */
    @Transactional
    public int judgeUser(long userId) {
        ProfileRow profile = store.loadProfile(userId).orElse(null);
        if (profile == null) {
            return 0;
        }
        LocalDate today = LocalDate.now(clock.withZone(KOREA));
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC)); // DB 시각은 UTC로 저장 (D-72)
        List<ResultRow> rows = batch.judgeAll(profile, store.loadActiveBenefits(), store.loadRulesOfActiveBenefits(),
                store.loadRegionNames(), store.loadResults(userId), today, now);
        store.saveResults(userId, rows);
        return rows.size();
    }
}
