package com.bokdeongi.backend.sync;

import java.time.LocalDateTime;

/** DB에 있는 혜택의 동기화 상태 (benefits 한 줄 일부) */
public record ExistingBenefit(long id, String serviceId, LocalDateTime apiModifiedAt, String status, Integer viewCount,
                              String syncError) {
}
