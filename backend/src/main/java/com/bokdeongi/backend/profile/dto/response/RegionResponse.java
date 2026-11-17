package com.bokdeongi.backend.profile.dto.response;

import com.bokdeongi.backend.profile.entity.Region;

/**
 * 지역 목록 조회 응답 DTO.
 * 시·도 및 시군구 선택에 필요한 정보를 반환한다.
 */
public record RegionResponse(
        String regionCode,
        String sidoName,
        String sigunguName
) {

    /**
     * Region 엔티티를 응답 DTO로 변환한다.
     */
    public static RegionResponse from(Region region) {
        return new RegionResponse(
                region.getRegionCode(),
                region.getSidoName(),
                region.getSigunguName()
        );
    }
}