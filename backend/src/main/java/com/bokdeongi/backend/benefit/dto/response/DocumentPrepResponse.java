package com.bokdeongi.backend.benefit.dto.response;

import java.util.List;

/**
 * ② 필요 서류 (ASST-04, GET /api/applications/{id}/documents의 서류 부분).
 * 준비 완료 체크 상태(doc_checks)는 신청 건(ASST-01)이 생기면 컨트롤러에서 붙인다.
 *
 * @param groups   발급하는 곳별 묶음 (서류가 많은 곳부터)
 * @param forms    신청 기관 서식 (신청서·동의서 등, 접수처 비치 또는 공고 누리집에서 내려받기)
 * @param unknown  서류 목록에 없는 서류 (공고 원문 이름 그대로, 문의처와 함께 보여 준다)
 * @param contacts 문의처
 * @param total    준비할 서류 수 (groups + forms + unknown)
 */
public record DocumentPrepResponse(List<Group> groups, List<String> forms, List<String> unknown,
                                   List<ContactResponse> contacts, int total) {

    /** 발급하는 곳 하나 ("정부24", "대법원 전자가족관계등록시스템", "진료받은 병·의원" ...) */
    public record Group(String place, String url, List<Item> items) {
    }

    /**
     * 서류 하나.
     *
     * @param documentTypeId 준비 완료 체크(PATCH .../documents/{documentTypeId})에 쓰는 id
     * @param name           서류 목록의 이름
     * @param sourceName     공고 원문에 적힌 이름
     * @param validityDays   흔히 요구하는 유효기간(일). 공고 원문에 기한이 있으면 그쪽이 우선
     * @param ocrSupported   ③ 서류 검토에서 사진을 올려 확인할 수 있는 서류 (D-18)
     */
    public record Item(Long documentTypeId, String name, String sourceName, String issuer, boolean onlineAvailable,
                       String issueMethod, Integer validityDays, String alternatives, boolean ocrSupported) {
    }
}
