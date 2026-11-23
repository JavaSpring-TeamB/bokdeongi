package com.bokdeongi.backend.benefit.ask;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.common.privacy.PersonalInfoMasker;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import com.bokdeongi.backend.benefit.service.VisitChecklistBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BEN-08 공고문에 질문하기 (BN-08 POST /api/benefits/{id}/ask).
 *
 * <ul>
 *   <li>이 혜택의 공고 원문만 넣고 답하게 한다. 근거 문장은 원문에 글자 그대로 있는 것만 보여 준다 (AskChecker)</li>
 *   <li>원문에 답이 없거나 검사에 걸리면 "공고에서 못 찾았어요" + 문의처</li>
 *   <li>자격 결론은 내지 않는다. 자격 질문이면 eligibility = true로 돌려 프론트가 규칙 엔진 판정 결과로 연결한다</li>
 *   <li>질문·답변은 저장하지 않는다. 1인 하루 30회 (AskLimiter)</li>
 *   <li>질문 속 주민등록번호·전화번호·이메일은 LLM에 보내기 전에 가린다 (D-92: 회원 정보는 무료 LLM에 보내지 않음.
 *       개발·시연에서는 테스트·페르소나 질문만 쓴다)</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);

    static final int MAX_QUESTION = 200;

    /** 자격을 묻는 질문 */
    private static final Pattern ELIGIBILITY_Q = Pattern.compile(
            "받을\\s*수\\s*있|신청할\\s*수\\s*있|신청\\s*가능|대상(?:인가|이에요|이야|이니|일까|이 ?되|에 ?해당)|해당(?:되|돼|하)|자격");

    private final BenefitRepository benefitRepository;
    private final AskGenerator generator;
    private final AskLimiter limiter;

    public AskService(BenefitRepository benefitRepository, AskGenerator generator, AskLimiter limiter) {
        this.benefitRepository = benefitRepository;
        this.generator = generator;
        this.limiter = limiter;
    }

    public AskDto.Response ask(long userId, long benefitId, String question) {
        String q = question == null ? "" : question.strip();
        if (q.isEmpty()) {
            throw AskException.invalidInput("질문을 입력해 주세요.");
        }
        if (q.length() > MAX_QUESTION) {
            throw AskException.invalidInput("질문은 " + MAX_QUESTION + "자 이내로 써 주세요.");
        }
        Benefit b = benefitRepository.findById(benefitId).orElseThrow(AskException::notFound);
        if (!generator.available()) {
            throw AskException.disabled(); // 키가 없으면 횟수를 쓰지 않는다
        }
        if (!limiter.tryUse(userId)) {
            throw AskException.tooMany();
        }

        String raw;
        try {
            raw = generator.generate(AskPrompt.system(), AskPrompt.user(b.getTitle(), b.getOrgName(), b.getTargetText(),
                    b.getCriteriaText(), b.getContentText(), b.getApplyMethodText(), b.getApplyPeriodText(),
                    b.getRequiredDocsText(), mask(q)));
        } catch (RuntimeException e) {
            // 질문 글은 남기지 않는다 (D-92). 예외 종류·메시지만
            log.warn("공고문 질문 LLM 호출 실패 benefitId={}: {}", benefitId, e.toString());
            throw AskException.unavailable();
        }

        boolean eligibilityQ = ELIGIBILITY_Q.matcher(q).find();
        AskChecker.Result r = AskChecker.check(raw, sources(b));
        int remaining = limiter.remaining(userId);
        if (!r.ok()) {
            return new AskDto.Response("공고에서 답을 찾지 못했어요. " + contactLine(b), false, List.of(), eligibilityQ,
                    remaining, AskDto.NOTICE);
        }
        String answer = r.found() ? r.answer() : r.answer() + " " + contactLine(b);
        return new AskDto.Response(answer, r.found(), r.quotes(), r.eligibility() || eligibilityQ, remaining,
                AskDto.NOTICE);
    }

    /** 질문 속 개인정보를 가린다 */
    static String mask(String q) {
        return PersonalInfoMasker.mask(q);
    }

    /** "문의처(경제과 033-560-2324)에 확인해 보세요." */
    static String contactLine(Benefit b) {
        List<VisitChecklistBuilder.Contact> contacts = VisitChecklistBuilder.contacts(b.getContact());
        if (!contacts.isEmpty()) {
            VisitChecklistBuilder.Contact c = contacts.get(0);
            String who = (c.name() + " " + c.phone()).strip();
            if (!who.isEmpty()) {
                return "문의처(" + who + ")에 확인해 보세요.";
            }
        }
        if (b.getOrgName() != null && !b.getOrgName().isBlank()) {
            return "소관기관(" + b.getOrgName().strip() + ")에 확인해 보세요.";
        }
        return "소관기관에 확인해 보세요.";
    }

    /** 근거·숫자 대조에 쓰는 공고 원문 칸 */
    static List<String> sources(Benefit b) {
        List<String> out = new ArrayList<>();
        for (String s : new String[] {b.getTitle(), b.getOrgName(), b.getTargetText(), b.getCriteriaText(),
                b.getContentText(), b.getApplyMethodText(), b.getApplyPeriodText(), b.getRequiredDocsText(),
                b.getContact()}) {
            if (s != null && !s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }
}
