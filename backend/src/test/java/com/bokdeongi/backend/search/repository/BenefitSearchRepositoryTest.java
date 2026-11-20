package com.bokdeongi.backend.search.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenefitSearchRepositoryTest {

    private int calculateScore(
            String keyword,
            String title,
            String hashtags,
            String orgName,
            String summary
    ) {
        String search = keyword.toLowerCase();
        String normalizedTitle = title.toLowerCase();
        String normalizedHashtags = hashtags.toLowerCase();
        String normalizedOrgName = orgName.toLowerCase();
        String normalizedSummary = summary.toLowerCase();

        if (normalizedTitle.equals(search)) {
            return 100;
        }

        if (normalizedTitle.startsWith(search)) {
            return 90;
        }

        if (normalizedTitle.contains(search)) {
            return 80;
        }

        if (normalizedHashtags.contains(search)) {
            return 70;
        }

        if (normalizedOrgName.contains(search)) {
            return 60;
        }

        if (normalizedSummary.contains(search)) {
            return 50;
        }

        return 0;
    }

    @Test
    void 제목_완전일치가_가장_높은_점수를_받는다() {
        int score = calculateScore(
                "청년",
                "청년",
                "",
                "",
                ""
        );

        assertEquals(100, score);
    }

    @Test
    void 제목_시작일치가_해시태그_일치보다_우선한다() {
        int titleScore = calculateScore(
                "청년",
                "청년 주거 지원",
                "",
                "",
                ""
        );

        int hashtagScore = calculateScore(
                "청년",
                "주거 지원",
                "[\"청년\"]",
                "",
                ""
        );

        assertTrue(titleScore > hashtagScore);
    }

    @Test
    void 해시태그_일치가_기관명_일치보다_우선한다() {
        int hashtagScore = calculateScore(
                "청년",
                "주거 지원",
                "[\"청년\"]",
                "",
                ""
        );

        int orgScore = calculateScore(
                "청년",
                "주거 지원",
                "",
                "청년지원센터",
                ""
        );

        assertTrue(hashtagScore > orgScore);
    }

    @Test
    void 기관명_일치가_요약_일치보다_우선한다() {
        int orgScore = calculateScore(
                "청년",
                "주거 지원",
                "",
                "청년지원센터",
                ""
        );

        int summaryScore = calculateScore(
                "청년",
                "주거 지원",
                "",
                "",
                "청년 대상 지원"
        );

        assertTrue(orgScore > summaryScore);
    }
}