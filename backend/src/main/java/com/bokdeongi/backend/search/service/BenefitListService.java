package com.bokdeongi.backend.search.service;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.search.dto.request.BenefitListRequest;
import com.bokdeongi.backend.search.entity.SearchLog;
import com.bokdeongi.backend.search.repository.BenefitSearchRepository;
import com.bokdeongi.backend.search.repository.SearchLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BenefitListService {

    private final BenefitSearchRepository benefitSearchRepository;
    private final SearchLogRepository searchLogRepository;

    public BenefitListService(
            BenefitSearchRepository benefitSearchRepository,
            SearchLogRepository searchLogRepository
    ) {
        this.benefitSearchRepository = benefitSearchRepository;
        this.searchLogRepository = searchLogRepository;
    }

    @Transactional
    public Page<Benefit> findBenefits(BenefitListRequest request) {

        Page<Benefit> result = benefitSearchRepository.findBenefits(
                request,
                PageRequest.of(request.page(), request.size())
        );

        if (request.q() != null && request.page() == 0) {
            searchLogRepository.save(
                    new SearchLog(
                            request.q(),
                            Math.toIntExact(result.getTotalElements())
                    )
            );
        }

        return result;
    }
}