package com.bokdeongi.backend.benefit.easy;

import com.bokdeongi.backend.benefit.dto.response.EasyResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EASY-01 공고문 쉬운 말 + 3줄 요약 (BN-07 easy=true, 배치 6단계).
 *
 * <ul>
 *   <li>공고가 그대로면(ai_source_modified_at = api_modified_at) 저장해 둔 글을 쓴다. 같은 원문으로 실패한 적이 있으면
 *       다시 부르지 않는다 (무료 한도 아끼기, 배치가 다시 만든다)</li>
 *   <li>없으면 LLM으로 만들고 EasyChecker로 검사해 통과하면 저장, 걸리면 FAILED로 저장</li>
 *   <li>LLM 호출 자체가 실패하면(한도·시간 초과) 저장하지 않고 FAILED만 돌려준다 → 다음 조회 때 다시</li>
 *   <li>LLM 구현(EasyGenerator)이 아직 없으면 FAILED → 프론트는 원문만 보여 준다</li>
 * </ul>
 */
@Service
public class EasyTextService {

    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    private final ObjectProvider<EasyGenerator> generator;
    private final BenefitRepository benefitRepository;
    private final Clock clock;

    @Autowired
    public EasyTextService(ObjectProvider<EasyGenerator> generator, BenefitRepository benefitRepository) {
        this(generator, benefitRepository, Clock.systemDefaultZone());
    }

    EasyTextService(ObjectProvider<EasyGenerator> generator, BenefitRepository benefitRepository, Clock clock) {
        this.generator = generator;
        this.benefitRepository = benefitRepository;
        this.clock = clock;
    }

    @Transactional
    public EasyResponse easy(Benefit benefit) {
        boolean fresh = benefit.getAiGeneratedAt() != null
                && Objects.equals(benefit.getAiSourceModifiedAt(), benefit.getApiModifiedAt());
        if (fresh && DONE.equals(benefit.getAiStatus()) && benefit.getEasyText() != null) {
            return done(benefit);
        }
        if (fresh && FAILED.equals(benefit.getAiStatus())) {
            return EasyResponse.failed();
        }
        EasyGenerator g = generator.getIfAvailable();
        if (g == null) {
            return EasyResponse.failed();
        }
        String json;
        try {
            json = g.generate(EasyPrompt.system(), EasyPrompt.user(benefit.getTitle(), benefit.getOrgName(),
                    benefit.getTargetText(), benefit.getCriteriaText(), benefit.getContentText(),
                    benefit.getApplyMethodText(), benefit.getApplyPeriodText(), benefit.getContact()));
        } catch (RuntimeException e) {
            return EasyResponse.failed();
        }
        EasyChecker.Result r = EasyChecker.check(json, sources(benefit));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!r.ok()) {
            benefit.applyEasy(FAILED, null, null, null, null, null, now);
            benefitRepository.save(benefit);
            return EasyResponse.failed();
        }
        EasyChecker.EasyText e = r.easy();
        benefit.applyEasy(DONE, e.who(), e.what(), e.how(), e.when(), e.text(), now);
        benefitRepository.save(benefit);
        return done(benefit);
    }

    /** 숫자 대조에 쓰는 공고 원문 칸 */
    static List<String> sources(Benefit b) {
        List<String> out = new ArrayList<>();
        for (String s : new String[] {b.getTitle(), b.getOrgName(), b.getTargetText(), b.getCriteriaText(),
                b.getContentText(), b.getApplyMethodText(), b.getApplyPeriodText(), b.getContact()}) {
            if (s != null && !s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }

    private static EasyResponse done(Benefit b) {
        return new EasyResponse(DONE, b.getEasyText(),
                new EasyResponse.Summary(b.getSummaryWho(), b.getSummaryWhat(), b.getSummaryHow(), b.getSummaryWhen()),
                b.getAiGeneratedAt(), EasyResponse.NOTICE);
    }
}
