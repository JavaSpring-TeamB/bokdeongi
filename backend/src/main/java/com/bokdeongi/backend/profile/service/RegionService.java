package com.bokdeongi.backend.profile.service;

import com.bokdeongi.backend.profile.dto.response.RegionResponse;
import com.bokdeongi.backend.profile.repository.RegionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 지역 정보 조회 서비스.
 * 전국 시·도 및 시군구 목록을 제공한다.
 */
@Service
public class RegionService {

    private final RegionRepository regionRepository;

    public RegionService(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    /**
     * 전국 지역 목록을 정렬 순서대로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<RegionResponse> getRegions() {
        return regionRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(RegionResponse::from)
                .toList();
    }

    /**
     * 선택한 시·도에 속한 시군구 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public List<RegionResponse> getSigungu(String sidoName) {
        return regionRepository
                .findBySidoNameAndSigunguNameIsNotNullOrderBySortOrderAsc(sidoName)
                .stream()
                .map(RegionResponse::from)
                .toList();
    }
}