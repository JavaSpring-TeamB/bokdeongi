package com.bokdeongi.backend.benefit.service;

import com.bokdeongi.backend.benefit.dto.response.BenefitDetailResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BenefitDetailService {

    private final BenefitRepository benefitRepository;

    public BenefitDetailService(
            BenefitRepository benefitRepository
    ) {
        this.benefitRepository = benefitRepository;
    }

    @Transactional(readOnly = true)
    public BenefitDetailResponse getDetail(Long benefitId) {
        Benefit benefit = benefitRepository.findById(benefitId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 혜택입니다."
                ));

        return BenefitDetailResponse.from(benefit);
    }
}