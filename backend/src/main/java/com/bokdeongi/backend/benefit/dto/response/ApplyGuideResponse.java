package com.bokdeongi.backend.benefit.dto.response;

import java.util.List;

/**
 * ④ 신청 안내 (ASST-05, ASST-06, GET /api/applications/{id}/guide).
 * 신청 완료 체크(AS-10 PATCH .../status)는 신청 건(ASST-01)이 생기면 컨트롤러에서 붙인다.
 *
 * @param method    ONLINE / VISIT / ONLINE_OR_VISIT / OTHER(우편·전화·자동 지급 등)
 * @param applyUrl  공식 신청 페이지. 모르면 null (그때는 detailUrl로 공고 원문을 보여 준다)
 * @param siteName  신청 누리집 이름 ("복지로", "정부24" ...). 모르면 null
 * @param detailUrl 공고 상세 페이지 (정부24 공공서비스)
 * @param steps     신청 단계 (신청방법 원문 순서)
 * @param notes     참고 사항 (원문의 ※, * 줄)
 * @param copyCards 신청서에 붙여 넣을 내 정보 (주민등록번호 없음)
 * @param visit     방문 신청 준비물·접수처 (ASST-06). 방문 신청이 아니면 null
 * @param contacts  문의처
 */
public record ApplyGuideResponse(String method, String applyUrl, String siteName, String detailUrl,
                                 List<Step> steps, List<String> notes, List<CopyCard> copyCards,
                                 VisitGuideResponse visit, List<ContactResponse> contacts) {

    /** @param method ONLINE / VISIT / PHONE / MAIL, 원문에서 알 수 없으면 null */
    public record Step(int order, String method, String text) {
    }

    public record CopyCard(String key, String label, String value) {
    }
}
