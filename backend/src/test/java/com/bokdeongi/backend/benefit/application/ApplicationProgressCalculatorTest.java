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
}