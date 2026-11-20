package com.bokdeongi.backend.search.service;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.search.repository.BenefitSearchRepository;
import com.bokdeongi.backend.search.repository.SearchLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BenefitSearchServiceTest {

    @Mock
    private BenefitSearchRepository benefitSearchRepository;

    @Mock
    private SearchLogRepository searchLogRepository;

    @InjectMocks
    private BenefitSearchService benefitSearchService;

    private Page<Benefit> emptyPage;

    @BeforeEach
    void setUp() {
        emptyPage = new PageImpl<>(List.of());
    }

    @Test
    void 정상_검색시_검색기록을_저장한다() {
        when(benefitSearchRepository.searchByKeyword(
                eq("청년"), any(Pageable.class)
        )).thenReturn(emptyPage);

        benefitSearchService.search("청년", 0, 10);

        verify(benefitSearchRepository).searchByKeyword(
                eq("청년"), any(Pageable.class)
        );

        verify(searchLogRepository).save(argThat(log ->
                log.getKeyword().equals("청년")
                        && log.getResultCount() == 0
        ));
    }

    @Test
    void 검색어_앞뒤_공백을_제거한다() {
        when(benefitSearchRepository.searchByKeyword(
                eq("청년"), any(Pageable.class)
        )).thenReturn(emptyPage);

        benefitSearchService.search("  청년  ", 0, 10);

        verify(benefitSearchRepository).searchByKeyword(
                eq("청년"), any(Pageable.class)
        );
    }

    @Test
    void 한글자_검색어는_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> benefitSearchService.search("청", 0, 10));

        verifyNoInteractions(
                benefitSearchRepository,
                searchLogRepository
        );
    }

    @Test
    void 특수문자만_입력하면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> benefitSearchService.search("@@@", 0, 10));

        verifyNoInteractions(
                benefitSearchRepository,
                searchLogRepository
        );
    }

    @Test
    void 잘못된_페이지번호는_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> benefitSearchService.search("청년", -1, 10));

        verifyNoInteractions(
                benefitSearchRepository,
                searchLogRepository
        );
    }

    @Test
    void 페이지크기가_100을_초과하면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> benefitSearchService.search("청년", 0, 101));

        verifyNoInteractions(
                benefitSearchRepository,
                searchLogRepository
        );
    }

    @Test
    void 두번째_페이지_검색시_검색기록을_저장하지_않는다() {
        when(benefitSearchRepository.searchByKeyword(
                eq("청년"), any(Pageable.class)
        )).thenReturn(emptyPage);

        benefitSearchService.search("청년", 1, 10);

        verify(benefitSearchRepository).searchByKeyword(
                eq("청년"),
                argThat(pageable ->
                        pageable.getPageNumber() == 1
                                && pageable.getPageSize() == 10
                )
        );

        verifyNoInteractions(searchLogRepository);
    }
}