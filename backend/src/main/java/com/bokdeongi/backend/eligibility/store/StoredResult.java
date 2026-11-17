package com.bokdeongi.backend.eligibility.store;

import java.time.LocalDateTime;

/** 이미 저장된 판정 결과 중 다시 판정할 때 이어받는 값. */
public record StoredResult(long benefitId, String result, LocalDateTime firstEligibleAt) {
}
