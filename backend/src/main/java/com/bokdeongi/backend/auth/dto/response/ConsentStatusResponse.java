package com.bokdeongi.backend.auth.dto.response;

import java.util.List;

/**
 * 회원의 약관 유형별 최신 동의 상태 응답.
 *
 * @param consents 약관 유형별 최신 동의 정보
 */
public record ConsentStatusResponse(
        List<ConsentItem> consents
) {

    /**
     * 개별 약관의 최신 동의 정보.
     *
     * @param consentType 약관 유형
     * @param agreed 동의 여부, 이력이 없으면 null
     * @param docVersion 동의 문서 버전
     * @param consentId 최신 동의 이력 ID, 이력이 없으면 null
     */
    public record ConsentItem(
            String consentType,
            Boolean agreed,
            String docVersion,
            Long consentId
    ) {
    }
}