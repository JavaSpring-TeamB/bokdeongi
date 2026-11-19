package com.bokdeongi.backend.profile.dto.response;

import com.bokdeongi.backend.profile.entity.Region;

/**
 * 지역 목록 조회 응답 DTO.
 * 시·도 및 시군구 선택에 필요한 정보를 반환한다.
 *
 * @param regionCode 지역 코드
 * @param sidoName 시·도 이름
 * @param sigunguName 시군구 이름
 * @param sortOrder 지역 정렬 순서
 */
public record RegionResponse(
        String regionCode,
        String sidoName,
        String sigunguName,
        Short sortOrder
) {

    /**
     * Region 엔티티를 응답 DTO로 변환한다.
     *
     * @param region 지역 엔티티
     * @return 지역 응답 DTO
     */
    public static RegionResponse from(Region region) {
        return new RegionResponse(
                region.getRegionCode(),
                region.getSidoName(),
                region.getSigunguName(),
                region.getSortOrder()
        );
    }
}