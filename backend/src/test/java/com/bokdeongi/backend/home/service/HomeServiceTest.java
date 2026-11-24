package com.bokdeongi.backend.home.service;

import com.bokdeongi.backend.benefit.recommend.RecommendResponse;
import com.bokdeongi.backend.benefit.recommend.RecommendService;
import com.bokdeongi.backend.home.dto.response.HomeSummaryResponse;
import com.bokdeongi.backend.home.repository.HomeQueryRepository;
import com.bokdeongi.backend.home.repository.HomeQueryRepository.EligibilityCountRow;
import com.bokdeongi.backend.profile.dto.response.ProfileResponse;
import com.bokdeongi.backend.profile.service.ProfileService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    private static final long USER_ID = 1L;

    private static final LocalDateTime UPDATED_AT =
            LocalDateTime.of(2026, 11, 23, 5, 50, 39);

    @Mock
    private HomeQueryRepository homeQueryRepository;

    @Mock
    private ProfileService profileService;

    @Mock
    private RecommendService recommendService;

    @InjectMocks
    private HomeService homeService;

    @BeforeEach
    void setUp() {
        when(homeQueryRepository.findApplications(USER_ID))
                .thenReturn(List.of());
    }

    @Test
    void 프로필_미완성인_경우_통계는_0이고_갱신시각은_반환한다() {

        mockProfile(false);

        when(homeQueryRepository.findBenefitDataUpdatedAt())
                .thenReturn(UPDATED_AT);

        HomeSummaryResponse response =
                homeService.getSummary(USER_ID);

        assertThat(response.profileCompleted()).isFalse();
        assertThat(response.eligibilityUpdating()).isFalse();

        assertZeroCounts(response);

        assertThat(response.benefitDataUpdatedAt())
                .isEqualTo(UPDATED_AT);

        verify(homeQueryRepository, never())
                .findEligibilityCounts(USER_ID);

        verify(homeQueryRepository, never())
                .findEligibleBenefits(USER_ID);

        verifyNoInteractions(recommendService);
    }

    @Test
    void 판정이_완료된_경우_실제_통계와_갱신시각을_반환한다() {

        mockProfile(true);
        mockRecommendation(false);

        when(homeQueryRepository.findEligibleBenefits(USER_ID))
                .thenReturn(List.of());

        when(homeQueryRepository.findEligibilityCounts(USER_ID))
                .thenReturn(
                        new EligibilityCountRow(
                                100L, 20L, 30L, 50L
                        )
                );

        when(homeQueryRepository.findBenefitDataUpdatedAt())
                .thenReturn(UPDATED_AT);

        HomeSummaryResponse response =
                homeService.getSummary(USER_ID);

        assertThat(response.profileCompleted()).isTrue();
        assertThat(response.eligibilityUpdating()).isFalse();

        assertThat(response.totalCount()).isEqualTo(100L);
        assertThat(response.eligibleCount()).isEqualTo(20L);
        assertThat(response.conditionalCount()).isEqualTo(30L);
        assertThat(response.ineligibleCount()).isEqualTo(50L);

        assertThat(response.totalCount()).isEqualTo(
                response.eligibleCount()
                        + response.conditionalCount()
                        + response.ineligibleCount()
        );

        assertThat(response.benefitDataUpdatedAt())
                .isEqualTo(UPDATED_AT);

        verify(homeQueryRepository)
                .findEligibilityCounts(USER_ID);
    }

    @Test
    void 재판정_중에는_통계를_조회하지_않고_0을_반환한다() {

        mockProfile(true);
        mockRecommendation(true);

        when(homeQueryRepository.findEligibleBenefits(USER_ID))
                .thenReturn(List.of());

        when(homeQueryRepository.findBenefitDataUpdatedAt())
                .thenReturn(UPDATED_AT);

        HomeSummaryResponse response =
                homeService.getSummary(USER_ID);

        assertThat(response.profileCompleted()).isTrue();
        assertThat(response.eligibilityUpdating()).isTrue();

        assertZeroCounts(response);

        assertThat(response.recommendations()).isEmpty();
        assertThat(response.nearMissBenefits()).isEmpty();

        assertThat(response.benefitDataUpdatedAt())
                .isEqualTo(UPDATED_AT);

        verify(homeQueryRepository, never())
                .findEligibilityCounts(USER_ID);
    }

    @Test
    void 정상_동기화_이력이_없으면_갱신시각은_null이다() {

        mockProfile(true);
        mockRecommendation(false);

        when(homeQueryRepository.findEligibleBenefits(USER_ID))
                .thenReturn(List.of());

        when(homeQueryRepository.findEligibilityCounts(USER_ID))
                .thenReturn(
                        new EligibilityCountRow(
                                100L, 20L, 30L, 50L
                        )
                );

        when(homeQueryRepository.findBenefitDataUpdatedAt())
                .thenReturn(null);

        HomeSummaryResponse response =
                homeService.getSummary(USER_ID);

        assertThat(response.benefitDataUpdatedAt()).isNull();

        assertThat(response.totalCount()).isEqualTo(100L);
        assertThat(response.eligibleCount()).isEqualTo(20L);
        assertThat(response.conditionalCount()).isEqualTo(30L);
        assertThat(response.ineligibleCount()).isEqualTo(50L);
    }

    // 프로필 작성 상태 설정
    private void mockProfile(boolean completed) {

        ProfileResponse profile = mock(ProfileResponse.class);

        when(profile.profileCompleted())
                .thenReturn(completed);

        when(profile.profileStep())
                .thenReturn((short) (completed ? 4 : 1));

        when(profileService.getProfile(USER_ID))
                .thenReturn(profile);
    }

    // 추천 응답 및 재판정 상태 설정
    private void mockRecommendation(boolean updating) {

        RecommendResponse response = new RecommendResponse(
                "ELIGIBLE",
                "RECOMMENDED",
                0,
                5,
                20L,
                4,
                Map.of(
                        "ELIGIBLE", 20L,
                        "CONDITIONAL", 30L,
                        "NEAR_MISS", 10L
                ),
                updating,
                new RecommendResponse.Filters(
                        List.of(),
                        List.of()
                ),
                List.of()
        );

        when(recommendService.recommended(
                USER_ID,
                "ELIGIBLE",
                null,
                null,
                "RECOMMENDED",
                0,
                5
        )).thenReturn(response);
    }

    // 모든 판정 통계가 0인지 검증
    private void assertZeroCounts(HomeSummaryResponse response) {

        assertThat(response.totalCount()).isZero();
        assertThat(response.eligibleCount()).isZero();
        assertThat(response.conditionalCount()).isZero();
        assertThat(response.ineligibleCount()).isZero();
    }
}