package com.bokdeongi.backend.eligibility.store;

import java.time.LocalDateTime;

/**
 * eligibility_results에 쓸 한 줄.
 *
 * @param exists 이미 저장된 줄이 있으면 true (UPDATE), 없으면 INSERT
 */
public record ResultRow(long benefitId, String result, String previousResult, boolean nearMiss, String nearMissReason,
        String missingFieldsJson, String traceJson, int profileVersion, LocalDateTime firstEligibleAt,
        LocalDateTime evaluatedAt, boolean exists) {
}
