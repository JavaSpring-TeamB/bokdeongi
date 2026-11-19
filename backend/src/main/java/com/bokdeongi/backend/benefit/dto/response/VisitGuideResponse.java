package com.bokdeongi.backend.benefit.dto.response;

import java.util.List;

/**
 * 방문 신청 준비물 (ASST-06, GET /api/applications/{id}/guide의 방문 부분).
 *
 * @param visitApply     방문 신청이 가능한 공고인지. false면 방문 안내를 보여 주지 않는다
 * @param items          준비물 (신분증이 맨 앞)
 * @param places         접수처 (최대 3곳)
 * @param placeFromOrg   접수처를 원문에서 못 찾아 소관기관으로 대신했는지 ("정확한 접수처는 문의처에 확인" 안내)
 * @param applyMethodText 신청방법 원문 (접수처를 다듬어 꺼내므로 원문도 같이 보여 준다)
 * @param contacts       문의처
 */
public record VisitGuideResponse(boolean visitApply, List<String> items, List<String> places, boolean placeFromOrg,
                                 String applyMethodText, List<ContactResponse> contacts) {
}
