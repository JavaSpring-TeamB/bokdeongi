package com.bokdeongi.backend.benefit.application;

import jakarta.validation.constraints.NotNull;

/**
 * 사용자의 신청 상태 변경 요청 DTO.
 *
 * 신청 완료, 선정, 미선정 및 취소 상태를 전달한다.
 * 현재 상태에서 변경 가능한지는 서비스에서 검증한다.
 */
public record ApplicationStatusUpdateRequest(
        @NotNull Status status
) {
    /**
     * 사용자가 직접 변경할 수 있는 신청 상태.
     */
    public enum Status {
        SUBMITTED,
        SELECTED,
        REJECTED,
        CANCELED
    }
}