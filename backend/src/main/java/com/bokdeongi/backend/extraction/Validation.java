package com.bokdeongi.backend.extraction;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 검증 결과.
 *
 * @param checks  검사별 결과: quote·numbers·code·repeat. true 통과, false 실패, null 확인 못 함
 * @param score   0.4×숫자 + 0.3×코드 + 0.3×반복 (확인 못 함은 0.5점, D-60). 버리면 0.00
 * @param dropped 버렸으면 true (근거 문구가 원문에 없거나 숫자·날짜가 근거와 다름)
 * @param reason  버린 이유
 * @param notes   참고 메모 (연도 추정, 미만·초과 환산 등)
 */
public record Validation(Map<String, Boolean> checks, BigDecimal score, boolean dropped, String reason,
        List<String> notes) {

    public Validation {
        checks = Collections.unmodifiableMap(new LinkedHashMap<>(checks));
        notes = List.copyOf(notes);
    }
}
