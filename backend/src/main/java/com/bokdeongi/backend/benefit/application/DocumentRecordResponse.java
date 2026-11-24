package com.bokdeongi.backend.benefit.application;

import java.time.LocalDate;
import java.util.List;

/**
 * 기억한 서류 기록 조회 응답 DTO
 */
public record DocumentRecordResponse(

        // 기억한 서류 기록 목록
        List<Item> records

) {

    /**
     * 개별 서류 기록 및 유효기간 정보
     */
    public record Item(

            // 서류 종류 ID
            Long documentTypeId,

            // 서류명
            String documentName,

            // 서류 발급일
            LocalDate issueDate,

            // 유효기간(일)
            Integer validityDays,

            // 유효기간 만료일
            LocalDate expiresAt,

            // 현재 유효 여부
            Boolean valid

    ) {
    }
}