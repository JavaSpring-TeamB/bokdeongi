package com.bokdeongi.backend.benefit.favorite;

/**
 * 혜택 관심 저장 상태 응답 DTO.
 *
 * 혜택 ID와 현재 관심 저장 여부를 반환하며,
 * 관심 등록, 해제 및 조회 API에서 공통으로 사용한다.
 */
public record FavoriteToggleResponse(
        long benefitId,
        boolean bookmarked
) {
}