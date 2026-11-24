package com.bokdeongi.backend.benefit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationProgressCalculatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void progressShouldNotExceed100Percent() throws Exception {
        JsonNode appAnswers = mapper.readTree("""
                {
                  "eligibilityConfirmed": true
                }
                """);

        JsonNode docChecks = mapper.readTree("""
                {
                  "documents": {
                    "268": {
                      "prepared": true,
                      "reviewed": true
                    },
                    "270": {
                      "prepared": true,
                      "reviewed": true
                    }
                  }
                }
                """);

        var result = ApplicationProgressCalculator.calculate(
                "SUBMITTED",
                4,
                appAnswers,
                docChecks,
                1,
                Set.of("268", "270")
        );

        assertEquals(4, result.completedItems());
        assertEquals(4, result.totalItems());
        assertEquals(100, result.progressPercent());
    }

    @Test
    void documentProgressShouldRequirePreparation() throws Exception {

        boolean[][] cases = {
                {false, false},
                {true, false},
                {false, true},
                {true, true}
        };

        int[] expected = {0, 1, 0, 2};

        for (int i = 0; i < cases.length; i++) {
            JsonNode appAnswers = mapper.readTree("{}");

            JsonNode docChecks = mapper.readTree("""
                {
                  "documents": {
                    "268": {
                      "prepared": %s,
                      "reviewed": %s
                    }
                  }
                }
                """.formatted(cases[i][0], cases[i][1]));

            var result = ApplicationProgressCalculator.calculate(
                    "IN_PROGRESS",
                    3,
                    appAnswers,
                    docChecks,
                    1,
                    Set.of("268")
            );

            assertEquals(
                    expected[i],
                    result.completedItems(),
                    "Failed case: prepared=" + cases[i][0]
                            + ", reviewed=" + cases[i][1]
            );
        }
    }

    @Test
    void invalidDocumentIdShouldNotAffectProgress() throws Exception {

        JsonNode appAnswers = mapper.readTree("{}");

        JsonNode docChecks = mapper.readTree("""
            {
              "documents": {
                "268": {
                  "prepared": true,
                  "reviewed": true
                },
                "999999": {
                  "prepared": true,
                  "reviewed": true
                }
              }
            }
            """);

        var result = ApplicationProgressCalculator.calculate(
                "IN_PROGRESS",
                3,
                appAnswers,
                docChecks,
                2,
                Set.of("268", "270")
        );

        assertEquals(2, result.completedItems());
        assertEquals(6, result.totalItems());
        assertEquals(33, result.progressPercent());
    }

    @Test
    void applicationStatusShouldDetermineStage() throws Exception {

        JsonNode appAnswers = mapper.readTree("{}");
        JsonNode docChecks = mapper.readTree("{}");

        String[] statuses = {
                "IN_PROGRESS",
                "SUBMITTED",
                "SELECTED",
                "REJECTED"
        };

        String[] expectedStages = {
                "준비 중",
                "신청 완료",
                "결과",
                "결과"
        };

        int[] expectedCompleted = {0, 1, 1, 1};

        for (int i = 0; i < statuses.length; i++) {

            var result = ApplicationProgressCalculator.calculate(
                    statuses[i],
                    3,
                    appAnswers,
                    docChecks,
                    1,
                    Set.of("268")
            );

            assertEquals(
                    expectedStages[i],
                    result.stage(),
                    "Failed status: " + statuses[i]
            );

            assertEquals(
                    expectedCompleted[i],
                    result.completedItems(),
                    "Failed completedItems: " + statuses[i]
            );
        }
    }

    @Test
    void applicationStepShouldDetermineStage() throws Exception {

        JsonNode appAnswers = mapper.readTree("{}");
        JsonNode docChecks = mapper.readTree("{}");

        int[] steps = {1, 2, 3, 4};

        String[] expectedStages = {
                "준비 중",
                "준비 중",
                "준비 중",
                "준비 중"
        };

        for (int i = 0; i < steps.length; i++) {

            var result = ApplicationProgressCalculator.calculate(
                    "IN_PROGRESS",
                    steps[i],
                    appAnswers,
                    docChecks,
                    1,
                    Set.of("268")
            );

            assertEquals(
                    expectedStages[i],
                    result.stage(),
                    "Failed step: " + steps[i]
            );

            assertEquals(0, result.completedItems());
            assertEquals(4, result.totalItems());
            assertEquals(0, result.progressPercent());
        }
    }

    // 필요 서류가 없고 자격 확인 전인 경우
    @Test
    void noDocumentsBeforeEligibilityConfirmation() throws Exception {

        var result = ApplicationProgressCalculator.calculate(
                "IN_PROGRESS",
                1,
                mapper.readTree("{}"),
                mapper.readTree("{}"),
                0,
                Set.of()
        );

        assertEquals("준비 중", result.stage());
        assertEquals(0, result.completedItems());
        assertEquals(2, result.totalItems());
        assertEquals(0, result.progressPercent());
    }

    // 필요 서류가 없고 자격 확인을 완료한 경우
    @Test
    void noDocumentsAfterEligibilityConfirmation() throws Exception {

        var result = ApplicationProgressCalculator.calculate(
                "IN_PROGRESS",
                2,
                mapper.readTree("""
                    {"eligibilityConfirmed": true}
                    """),
                mapper.readTree("{}"),
                0,
                Set.of()
        );

        assertEquals("서류 준비 완료", result.stage());
        assertEquals(1, result.completedItems());
        assertEquals(2, result.totalItems());
        assertEquals(50, result.progressPercent());
    }

    // 필요 서류가 없는 혜택의 신청 제출 완료
    @Test
    void noDocumentsAfterSubmission() throws Exception {

        var result = ApplicationProgressCalculator.calculate(
                "SUBMITTED",
                4,
                mapper.readTree("""
                    {"eligibilityConfirmed": true}
                    """),
                mapper.readTree("{}"),
                0,
                Set.of()
        );

        assertEquals("신청 완료", result.stage());
        assertEquals(2, result.completedItems());
        assertEquals(2, result.totalItems());
        assertEquals(100, result.progressPercent());
    }

    // 전체 서류 수와 유효 서류 키 개수가 불일치하는 경우
    @Test
    void mismatchedDocumentCountShouldNotCompleteStage()
            throws Exception {

        var result = ApplicationProgressCalculator.calculate(
                "IN_PROGRESS",
                3,
                mapper.readTree("""
                    {"eligibilityConfirmed": true}
                    """),
                mapper.readTree("""
                    {
                      "documents": {
                        "268": {
                          "prepared": true,
                          "reviewed": true
                        }
                      }
                    }
                    """),
                2,
                Set.of("268")
        );

        assertEquals("서류 검토 중", result.stage());
        assertEquals(3, result.completedItems());
        assertEquals(6, result.totalItems());
        assertEquals(50, result.progressPercent());
    }
}