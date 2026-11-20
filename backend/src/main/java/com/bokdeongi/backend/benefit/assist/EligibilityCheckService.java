package com.bokdeongi.backend.benefit.assist;

import com.bokdeongi.backend.benefit.recommend.RecommendQuery;
import com.bokdeongi.backend.benefit.recommend.RecommendRow;
import com.bokdeongi.backend.benefit.recommend.RecommendSort;
import com.bokdeongi.backend.benefit.recommend.RecommendTab;
import com.bokdeongi.backend.benefit.recommend.RecommendView;
import com.bokdeongi.backend.eligibility.service.EligibilityService;
import com.bokdeongi.backend.eligibility.store.ResultRow;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * 신청 도우미 ① 자격 확인 + 추가 질문 (ASST-02).
 *
 * <p>들어올 때마다 그 혜택만 다시 판정해(프로필·답이 바뀌었을 수 있어서) 조건별 충족/확인 필요/미충족과 근거 원문을 돌려준다.
 * 추가 질문의 답은 기존 PUT /api/me/answers(benefitId 포함)로 저장하고, 화면은 이 조회를 다시 부르면 된다.
 * 신청 건(ASST-01)과 진행 단계 저장은 A가 맡는다. A의 POST /api/applications/{id}/eligibility가 신청 건의
 * benefitId로 {@link #check(long, long)}을 부르고, '직접 확인했어요' 체크는 신청 건 진행 상태에 저장한다.
 */
@Service
public class EligibilityCheckService {

    static final int SIMILAR_LIMIT = 3;
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final EligibilityService eligibility;
    private final EligibilityCheckStore store;
    private final RecommendQuery recommend;

    public EligibilityCheckService(EligibilityService eligibility, EligibilityCheckStore store,
            RecommendQuery recommend) {
        this.eligibility = eligibility;
        this.store = store;
        this.recommend = recommend;
    }

    /** @return 혜택이 없거나 종료됐거나 개인 대상이 아니면 비어 있다 */
    public Optional<EligibilityCheckResponse> check(long userId, long benefitId) {
        Optional<ResultRow> judged = eligibility.judgeUserBenefit(userId, benefitId);
        Optional<EligibilityCheckView.Source> source = store.source(benefitId);
        if (judged.isEmpty() || source.isEmpty()) {
            return Optional.empty();
        }
        ResultRow row = judged.get();
        List<EligibilityCheckResponse.Similar> similar = "INELIGIBLE".equals(row.result())
                ? similar(userId, benefitId, source.get().serviceField()) : List.of();
        return Optional.of(EligibilityCheckView.build(benefitId, row.result(), row.nearMissReason(),
                RecommendView.traceOf(row.traceJson()), source.get(), similar));
    }

    /** 같은 서비스 분야에서 지금 '해당'인 혜택, 마감 빠른 순 */
    List<EligibilityCheckResponse.Similar> similar(long userId, long benefitId, String serviceField) {
        if (serviceField == null || serviceField.isBlank()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(KOREA);
        List<EligibilityCheckResponse.Similar> out = new ArrayList<>();
        for (RecommendRow r : recommend.page(userId, RecommendTab.ELIGIBLE, new RecommendQuery.Filter(null, serviceField),
                RecommendSort.DEADLINE, 0, SIMILAR_LIMIT + 1)) {
            if (r.benefitId() != benefitId && out.size() < SIMILAR_LIMIT) {
                out.add(new EligibilityCheckResponse.Similar(r.benefitId(), r.title(),
                        RecommendView.period(r, today).text()));
            }
        }
        return out;
    }
}
