package com.bokdeongi.backend.benefit.recommend;

import com.bokdeongi.backend.benefit.recommend.RecommendResponse.Item;
import com.bokdeongi.backend.eligibility.EligibilityJudge;
import com.bokdeongi.backend.eligibility.service.EligibilityService;
import com.bokdeongi.backend.eligibility.service.ProfileChangedEvent;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공고별 추가 질문 답 저장 (BEN-04 인라인 질문, PROF-06 저장·재사용).
 *
 * <p>답을 users.answers에 합쳐 저장하고, 답한 카드는 바로 다시 판정해 돌려준다. 같은 질문을 쓰는 다른 혜택은
 * 커밋 뒤 {@link ProfileChangedEvent}로 전체 재판정된다.
 */
@Service
public class AnswerService {

    static final Set<String> VALUES = Set.of("Y", "N", EligibilityJudge.ANSWER_UNSURE);
    /** 라벨 지침의 ANSWER 키 형식: 영문 소문자로 시작, 소문자·숫자·밑줄 (data-lab/rule_labels README) */
    static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_]{0,49}");
    static final int MAX_ANSWERS = 20;
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final AnswerStore answers;
    private final EligibilityService eligibility;
    private final RecommendQuery query;
    private final ApplicationEventPublisher events;

    public AnswerService(AnswerStore answers, EligibilityService eligibility, RecommendQuery query,
            ApplicationEventPublisher events) {
        this.answers = answers;
        this.eligibility = eligibility;
        this.query = query;
        this.events = events;
    }

    @Transactional
    public AnswerResponse save(long userId, AnswerRequest request) {
        Map<String, String> incoming = validate(request);
        Map<String, String> merged = answers.lockAnswers(userId);
        merged.putAll(incoming);
        answers.saveAnswers(userId, merged);

        Item card = null;
        if (request.benefitId() != null) {
            eligibility.judgeUserBenefit(userId, request.benefitId());
            card = query.one(userId, request.benefitId())
                    .map(row -> RecommendView.toItem(row, tabOf(row), RecommendSort.RECOMMENDED, Long.MAX_VALUE,
                            LocalDate.now(KOREA)))
                    .orElse(null);
        }
        events.publishEvent(new ProfileChangedEvent(userId));
        return new AnswerResponse(merged, card);
    }

    static Map<String, String> validate(AnswerRequest request) {
        if (request == null || request.answers() == null || request.answers().isEmpty()) {
            throw new RecommendException("answers에 답을 하나 이상 넣어 주세요");
        }
        if (request.answers().size() > MAX_ANSWERS) {
            throw new RecommendException("한 번에 " + MAX_ANSWERS + "개까지 저장할 수 있습니다");
        }
        request.answers().forEach((key, value) -> {
            if (key == null || !KEY.matcher(key).matches()) {
                throw new RecommendException("질문 키 형식이 올바르지 않습니다: " + key);
            }
            if (value == null || !VALUES.contains(value)) {
                throw new RecommendException("답은 Y / N / UNSURE 중 하나입니다: " + key);
            }
        });
        return request.answers();
    }

    /** 카드 모양을 정할 탭. 해당 없음은 아깝게 탈락 카드 모양(이유 없음)으로 그린다 */
    static RecommendTab tabOf(RecommendRow row) {
        return switch (row.result()) {
            case "ELIGIBLE" -> RecommendTab.ELIGIBLE;
            case "CONDITIONAL" -> RecommendTab.CONDITIONAL;
            default -> RecommendTab.NEAR_MISS;
        };
    }
}
