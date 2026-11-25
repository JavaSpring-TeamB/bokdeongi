package com.bokdeongi.backend.benefit.application;

import java.util.List;

/**
 * 회원의 신청 현황 목록 응답 DTO.
 *
 * 전체 신청 건수 및 상태별 집계 정보를 제공하며,
 * 진행 중, 완료, 마감된 신청 목록을 구분하여 반환한다.
 */
public record ApplicationListResponse(
        Summary summary,
        List<Item> inProgress,
        List<Item> completed,
        List<Item> expired
) {

    // 신청 현황 상단 건수 요약
    public record Summary(
            int totalCount,
            int inProgressCount,
            int completedCount,
            int expiredCount
    ) {
    }

    // 신청 목록 개별 항목
    public record Item(
            long id,
            long benefitId,
            String benefitName,
            String appStatus,
            String stage,
            Integer step,
            int completedItems,
            int totalItems,
            int progressPercent,
            String deadline,
            String deadlineLabel,
            Integer dDay,
            boolean calendarAvailable,
            String calendarUnavailableReason
    ) {
    }
}