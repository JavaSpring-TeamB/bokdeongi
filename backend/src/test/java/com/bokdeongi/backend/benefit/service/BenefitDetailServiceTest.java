package com.bokdeongi.backend.benefit.service;

import com.bokdeongi.backend.benefit.dto.response.BenefitDetailResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 혜택 상세 조회 및 응답 데이터 변환 테스트.
 */
class BenefitDetailServiceTest {

    private BenefitRepository benefitRepository;
    private BenefitDetailService service;

    @BeforeEach
    void setUp() {
        benefitRepository = mock(BenefitRepository.class);
        service = new BenefitDetailService(benefitRepository);
    }

    /**
     * 존재하는 활성 혜택의 상세 정보를 반환한다.
     */
    @Test
    void shouldReturnBenefitDetail() {
        Long benefitId = 1L;

        Benefit benefit = mock(Benefit.class);

        when(benefit.getId()).thenReturn(benefitId);
        when(benefit.getTitle()).thenReturn("청년 지원 혜택");
        when(benefit.getStatus()).thenReturn(Benefit.STATUS_ACTIVE);

        when(benefitRepository.findById(benefitId))
                .thenReturn(Optional.of(benefit));

        BenefitDetailResponse response = service.getDetail(benefitId);

        assertThat(response.id()).isEqualTo(benefitId);
        assertThat(response.title()).isEqualTo("청년 지원 혜택");
        assertThat(response.status()).isEqualTo(Benefit.STATUS_ACTIVE);
        assertThat(response.ended()).isFalse();

        verify(benefitRepository).findById(benefitId);
    }

    /**
     * 존재하지 않는 혜택은 404 오류를 반환한다.
     */
    @Test
    void shouldThrowNotFoundWhenBenefitDoesNotExist() {
        Long benefitId = 999L;

        when(benefitRepository.findById(benefitId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getDetail(benefitId)
        );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * 종료 상태인 혜택은 종료 여부를 true로 반환한다.
     */
    @Test
    void shouldMarkEndedBenefit() {
        Benefit benefit = mock(Benefit.class);

        when(benefit.getStatus())
                .thenReturn(Benefit.STATUS_ENDED);

        BenefitDetailResponse response =
                BenefitDetailResponse.from(benefit);

        assertThat(response.ended()).isTrue();
    }

    /**
     * 만료 상태인 혜택도 종료 여부를 true로 반환한다.
     */
    @Test
    void shouldMarkExpiredBenefit() {
        Benefit benefit = mock(Benefit.class);

        when(benefit.getStatus())
                .thenReturn(Benefit.STATUS_EXPIRED);

        BenefitDetailResponse response =
                BenefitDetailResponse.from(benefit);

        assertThat(response.ended()).isTrue();
    }

    /**
     * null 또는 공백인 주요 정보는 누락 목록에 포함한다.
     */
    @Test
    void shouldReturnMissingFields() {
        Benefit benefit = mock(Benefit.class);

        when(benefit.getContentText()).thenReturn(null);
        when(benefit.getTargetText()).thenReturn("   ");

        BenefitDetailResponse response =
                BenefitDetailResponse.from(benefit);

        assertThat(response.missingFields())
                .containsExactlyInAnyOrder(
                        "contentText",
                        "targetText",
                        "applyMethodText",
                        "requiredDocsText",
                        "applyPeriodText"
                );
    }

    /**
     * 주요 정보가 모두 존재하면 누락 목록은 비어 있어야 한다.
     */
    @Test
    void shouldReturnEmptyMissingFieldsWhenDataExists() {
        Benefit benefit = mock(Benefit.class);

        when(benefit.getContentText()).thenReturn("지원 내용");
        when(benefit.getTargetText()).thenReturn("지원 대상");
        when(benefit.getApplyMethodText()).thenReturn("온라인 신청");
        when(benefit.getRequiredDocsText()).thenReturn("신청서");
        when(benefit.getApplyPeriodText()).thenReturn("상시 신청");
        when(benefit.getMaxAmount()).thenReturn(1000000);

        BenefitDetailResponse response =
                BenefitDetailResponse.from(benefit);

        assertThat(response.missingFields()).isEmpty();
    }
}