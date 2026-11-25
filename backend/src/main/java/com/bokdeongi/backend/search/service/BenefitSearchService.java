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

/**
 * BEN-11: 혜택 키워드 검색 서비스.
 *
 * 검색어와 페이지 정보를 검증하고 관련도 순으로 혜택을 조회한다.
 * 첫 페이지 검색 시 검색어와 전체 결과 건수를 기록한다.
 */
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

    /**
     * 검색어를 검증하고 페이지 단위로 혜택을 조회한다.
     * 페이지 번호가 0인 경우에만 검색 기록을 저장한다.
     */
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

    /**
     * 기본 페이지 번호와 페이지 크기를 적용하여 혜택을 검색한다.
     */
    @Transactional
    public Page<Benefit> search(String keyword) {
        return search(keyword, 0, DEFAULT_PAGE_SIZE);
    }

    /**
     * 검색어의 공백, 길이 및 문자·숫자 포함 여부를 검증한다.
     * 검증을 통과한 검색어의 앞뒤 공백을 제거하여 반환한다.
     */
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