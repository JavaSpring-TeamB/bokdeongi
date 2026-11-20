package com.bokdeongi.backend.search.service;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.search.entity.SearchLog;
import com.bokdeongi.backend.search.repository.BenefitSearchRepository;
import com.bokdeongi.backend.search.repository.SearchLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BenefitSearchService {

    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final BenefitSearchRepository benefitSearchRepository;
    private final SearchLogRepository searchLogRepository;

    public BenefitSearchService(
            BenefitSearchRepository benefitSearchRepository,
            SearchLogRepository searchLogRepository
    ) {
        this.benefitSearchRepository = benefitSearchRepository;
        this.searchLogRepository = searchLogRepository;
    }

    @Transactional
    public Page<Benefit> search(String keyword, int page, int size) {

        String normalizedKeyword = validateKeyword(keyword);

        if (page < 0) {
            throw new IllegalArgumentException(
                    "페이지 번호는 0 이상이어야 합니다."
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "페이지 크기는 1~100 사이여야 합니다."
            );
        }

        Pageable pageable = PageRequest.of(page, size);

        Page<Benefit> result = benefitSearchRepository.searchByKeyword(
                normalizedKeyword,
                pageable
        );

        if (page == 0) {
            searchLogRepository.save(
                    new SearchLog(
                            normalizedKeyword,
                            Math.toIntExact(result.getTotalElements())
                    )
            );
        }

        return result;
    }

    public Page<Benefit> search(String keyword) {
        return search(keyword, 0, DEFAULT_PAGE_SIZE);
    }

    private String validateKeyword(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException(
                    "검색어를 입력해 주세요."
            );
        }

        String normalized = keyword.trim();

        if (normalized.length() < 2) {
            throw new IllegalArgumentException(
                    "검색어는 2글자 이상 입력해 주세요."
            );
        }

        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "검색어는 100글자 이하로 입력해 주세요."
            );
        }

        if (!normalized.matches("(?s).*[\\p{L}\\p{N}].*")) {
            throw new IllegalArgumentException(
                    "검색어에 문자 또는 숫자를 포함해 주세요."
            );
        }

        return normalized;
    }
}