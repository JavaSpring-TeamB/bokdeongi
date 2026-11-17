package com.bokdeongi.backend.eligibility.repository;

import com.bokdeongi.backend.eligibility.entity.EligibilityResultEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EligibilityResultRepository extends JpaRepository<EligibilityResultEntity, Long> {

    List<EligibilityResultEntity> findByUserId(Long userId);

    Optional<EligibilityResultEntity> findByUserIdAndBenefitId(Long userId, Long benefitId);
}
