package com.bokdeongi.backend.benefit.service;

import com.bokdeongi.backend.benefit.dto.response.ApplyGuideResponse;
import com.bokdeongi.backend.benefit.dto.response.VisitGuideResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.profile.dto.response.ProfileResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * AI 신청 도우미 ④ 신청 안내 + 입력값 복사 카드 (ASST-05), 방문 준비물 (ASST-06). LLM을 부르지 않는다.
 *
 * <p>신청 건(ASST-01, /api/applications/{id})이 생기면 컨트롤러가 신청 건 → Benefit, 로그인 회원 → ProfileResponse를
 * 꺼내 이 서비스를 부른다. 그전까지는 Benefit과 프로필을 받아 응답을 만드는 데까지만 한다.
 */
@Service
public class ApplicationGuideService {

    private final ApplicationDocumentService documentService;

    public ApplicationGuideService(ApplicationDocumentService documentService) {
        this.documentService = documentService;
    }

    /** @param profile 로그인 회원 프로필. 없으면 복사 카드 없이 만든다 */
    public ApplyGuideResponse guide(Benefit benefit, ProfileResponse profile) {
        ApplyGuideBuilder.Guide g = ApplyGuideBuilder.build(new ApplyGuideBuilder.Input(
                benefit.getApplyMethodText(), benefit.getOnlineApplyUrl(), benefit.getOnlineApply()));
        VisitGuideResponse visit = documentService.visitGuide(benefit);

        List<ApplyGuideResponse.Step> steps = new ArrayList<>();
        for (ApplyGuideBuilder.Step s : g.steps()) {
            steps.add(new ApplyGuideResponse.Step(steps.size() + 1, s.method(), s.text()));
        }
        List<ApplyGuideResponse.CopyCard> cards = CopyCardBuilder.build(profile).stream()
                .map(c -> new ApplyGuideResponse.CopyCard(c.key(), c.label(), c.value()))
                .toList();
        return new ApplyGuideResponse(g.method(), g.applyUrl(), g.siteName(), benefit.getDetailUrl(), steps, g.notes(),
                cards, visit.visitApply() ? visit : null, visit.contacts());
    }
}
