package com.bokdeongi.backend.profile.repository;

import com.bokdeongi.backend.profile.entity.MedianIncomeStandard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 연도와 가구원 수에 따른 기준 중위소득 조회 Repository.
 */
public interface MedianIncomeStandardRepository
        extends JpaRepository<
        MedianIncomeStandard,
        MedianIncomeStandard.MedianIncomeStandardId> {

    /**
     * 지정한 연도와 가구원 수에 해당하는 월 기준 중위소득을 조회한다.
     */
    Optional<MedianIncomeStandard> findByYearAndHouseholdSize(
            Short year,
            Short householdSize
    );

    /**
     * 지정한 연도에 등록된 기준 중위소득 데이터 개수를 조회한다.
     */
    long countByYear(Short year);
}