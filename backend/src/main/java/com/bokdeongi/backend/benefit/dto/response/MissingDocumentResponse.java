package com.bokdeongi.backend.benefit.dto.response;

import java.util.List;

/**
 * ③ 부족 서류 보완 안내 (ASST-04, GET /api/applications/{id}/missing).
 * 아직 준비 안 된 서류(MISSING·INVALID)만 발급하는 곳별로 묶는다. 한 곳에서 몰아서 떼도록.
 *
 * @param groups   발급하는 곳별 부족 서류 (많은 곳부터)
 * @param forms    신청 기관 서식 (신청서·동의서 등, 상태를 따로 받지 않아 늘 보여 준다)
 * @param unknown  서류 목록에 없는 서류 (원문 이름 그대로, 문의처로 확인)
 * @param allReady 목록에 있는 서류가 모두 준비됨 → ④ 신청 안내로
 */
public record MissingDocumentResponse(List<Group> groups, List<String> forms, List<String> unknown,
                                      boolean allReady) {

    /**
     * @param issuer   발급하는 곳 ("정부24", "읍·면·동 행정복지센터" ...)
     * @param online   이 묶음 서류를 모두 온라인으로 뗄 수 있는지
     * @param issueUrl 발급 누리집. 없으면 null
     * @param visitTip 온라인이 아닐 때 방문 안내. 온라인이면 null
     */
    public record Group(String issuer, boolean online, String issueUrl, String visitTip, List<Doc> documents) {
    }

    /**
     * @param result       MISSING / INVALID
     * @param reason       부족한 이유 (서류 검토 결과 문구, 없으면 "아직 준비하지 않았어요")
     * @param howTo        발급 방법 (document_types.issue_method)
     * @param alternatives 대체 서류 (document_types.alternatives)
     * @param orDocs       공고에 "A 또는 B"로 적혀 대신 낼 수 있는 서류 이름
     */
    public record Doc(Long documentTypeId, String name, String sourceName, String result, String reason, String howTo,
                      String alternatives, List<String> orDocs, boolean ocrSupported) {
    }
}
