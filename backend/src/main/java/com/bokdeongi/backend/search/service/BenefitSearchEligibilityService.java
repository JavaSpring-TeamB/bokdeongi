package com.bokdeongi.backend.search.service;

import com.bokdeongi.backend.eligibility.entity.EligibilityResultEntity;
import com.bokdeongi.backend.eligibility.repository.EligibilityResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BenefitSearchEligibilityService {

    private final EligibilityResultRepository eligibilityResultRepository;

    public BenefitSearchEligibilityService(
            EligibilityResultRepository eligibilityResultRepository
    ) {
        this.eligibilityResultRepository = eligibilityResultRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getEligibilityResults(Long userId) {
        if (userId == null) {
            return Map.of();
        }

        return eligibilityResultRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(
                        EligibilityResultEntity::getBenefitId,
                        EligibilityResultEntity::getResult
                ));
    }
}