package com.bokdeongi.backend.profile.repository;

import com.bokdeongi.backend.profile.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 지역 정보 조회 Repository.
 * regions 테이블에서 시·도 및 시군구 목록을 조회한다.
 */
public interface RegionRepository extends JpaRepository<Region, String> {

    /**
     * 전국 지역 목록을 정렬 순서대로 조회한다.
     */
    List<Region> findAllByOrderBySortOrderAsc();

    /**
     * 선택한 시·도에 속한 시군구 목록을 조회한다.
     */
    List<Region> findBySidoNameAndSigunguNameIsNotNullOrderBySortOrderAsc(
            String sidoName
    );
}