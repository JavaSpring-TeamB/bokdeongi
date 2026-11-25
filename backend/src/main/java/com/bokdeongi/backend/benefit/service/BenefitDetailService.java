package com.bokdeongi.backend.benefit.service;

import com.bokdeongi.backend.benefit.dto.response.BenefitDetailResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 혜택 상세 정보를 조회하는 서비스.
 *
 * 혜택의 존재 여부를 확인하고 조회된 엔티티를
 * 상세 조회 응답 DTO로 변환한다.
 */
@Service
public class BenefitDetailService {

    private final BenefitRepository benefitRepository;

    public BenefitDetailService(
            BenefitRepository benefitRepository
    ) {
        this.benefitRepository = benefitRepository;
    }

    /**
     * 지정한 혜택 ID의 상세 정보를 조회한다.
     *
     * 종료된 혜택도 상세 정보 조회가 가능하며,
     * 존재하지 않는 혜택은 404 오류를 반환한다.
     */
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