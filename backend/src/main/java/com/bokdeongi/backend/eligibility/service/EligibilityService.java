package com.bokdeongi.backend.eligibility.service;

import com.bokdeongi.backend.common.crypto.FieldEncryptionService;
import com.bokdeongi.backend.eligibility.EligibilityJudge;
import com.bokdeongi.backend.eligibility.store.BenefitTarget;
import com.bokdeongi.backend.eligibility.store.EligibilityStore;
import com.bokdeongi.backend.eligibility.store.ProfileRow;
import com.bokdeongi.backend.eligibility.store.ResultRow;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 한 명의 혜택 전체를 판정해 eligibility_results에 저장한다 (ELIG-01, PROF-05 재매칭).
 *
 * <p>보통은 프로필이 저장된 뒤 {@link ProfileChangedEvent}로 비동기 호출된다. 이전 결과는 previous_result로,
 * 처음 해당된 시각은 first_eligible_at으로 이어받는다. 민감 항목은 sensitive_enc를 복호화해 판정에만 쓰고 저장하지 않는다.
 */
@Service
public class EligibilityService {

    private static final Logger log = LoggerFactory.getLogger(EligibilityService.class);

    /** 신청 마감·나이 계산은 한국 날짜 기준 */
    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final EligibilityStore store;
    private final FieldEncryptionService encryption;
    private final EligibilityBatch batch;
    private final Clock clock;

    // 생성자가 하나여야 스프링이 자동 주입한다. 판정 계산은 EligibilityBatch 단위 테스트로 검증한다
    public EligibilityService(EligibilityStore store, FieldEncryptionService encryption) {
        this.store = store;
        this.encryption = encryption;
        this.batch = new EligibilityBatch(new EligibilityJudge());
        this.clock = Clock.systemUTC();
    }

    /**
     * @return 저장한 결과 수. 회원이 없으면 0
     */
    @Transactional
    public int judgeUser(long userId) {
        ProfileRow profile = store.loadProfile(userId, this::decryptSensitive).orElse(null);
        if (profile == null) {
            return 0;
        }
        List<ResultRow> rows = batch.judgeAll(profile, store.loadActiveBenefits(), store.loadRulesOfActiveBenefits(),
                store.loadRegionNames(), store.loadResults(userId), today(), now());
        store.saveResults(userId, rows);
        return rows.size();
    }

    /**
     * 혜택 한 건만 바로 다시 판정해 저장한다. 조건부 카드에서 추가 질문에 답했을 때 배지를 즉시 바꾸는 용도 (BEN-04).
     *
     * @return 저장한 결과. 회원·혜택이 없거나 개인 추천 대상이 아니면 비어 있다
     */
    @Transactional
    public Optional<ResultRow> judgeUserBenefit(long userId, long benefitId) {
        Optional<ProfileRow> profile = store.loadProfile(userId, this::decryptSensitive);
        Optional<BenefitTarget> benefit = store.loadActiveBenefit(benefitId);
        if (profile.isEmpty() || benefit.isEmpty()) {
            return Optional.empty();
        }
        List<ResultRow> rows = batch.judgeAll(profile.get(), List.of(benefit.get()),
                Map.of(benefitId, store.loadRules(benefitId)), store.loadRegionNames(), store.loadResults(userId),
                today(), now());
        store.saveResults(userId, rows);
        return rows.stream().findFirst();
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(KOREA));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC)); // DB 시각은 UTC로 저장 (D-72)
    }

    /** 복호화에 실패하면(키 변경 등) 민감 항목을 '모름'으로 두고 판정은 계속한다 */
    private String decryptSensitive(byte[] encrypted) {
        try {
            return encryption.decrypt(encrypted);
        } catch (RuntimeException e) {
            log.warn("민감정보 복호화 실패: 민감 조건은 조건부로 판정합니다 ({})", e.getClass().getSimpleName());
            return null;
        }
    }
}
