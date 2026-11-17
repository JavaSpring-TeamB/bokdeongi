package com.bokdeongi.backend.profile.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PROF-01 단계형 프로필 조회 응답 DTO.
 *
 * 회원의 프로필 정보와 단계별 작성 진행 상태를
 * 프론트엔드에 전달한다.
 *
 * 민감정보 원본 및 암호화 데이터는 응답에 포함하지 않는다.
 */
public record ProfileResponse(

        // 회원 기본 정보
        Long userId,
        String name,
        String email,
        String phoneNumber,

        // 1단계: 기본 정보
        LocalDate birthDate,
        String gender,
        String regionCode,
        String sidoName,
        String sigunguName,

        // 2단계: 가구 정보
        Short householdSize,
        String incomeInputType,
        Short incomeBaseYear,
        BigDecimal medianIncomePct,
        String housingType,
        Boolean hasHouse,
        List<String> householdTypeCodes,

        // 3단계: 나의 상황
        List<String> statusCodes,
        String maritalStatus,
        Short childrenCount,
        Short youngestChildBirthYear,
        String welfareStatus,

        // 프로필 진행 상태
        Integer profileVersion,
        Short profileStep,
        boolean profileCompleted,
        LocalDateTime profileCompletedAt

) {
}