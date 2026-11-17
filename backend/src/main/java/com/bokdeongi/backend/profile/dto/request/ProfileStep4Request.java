package com.bokdeongi.backend.profile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * PROF-01 프로필 4단계: 특수 상황 입력 요청.
 *
 * 민감정보 수집 동의는 선택 사항이다.
 */
public record ProfileStep4Request(

        // 민감정보 수집 및 이용 동의 여부
        @NotNull(message = "민감정보 동의 여부는 필수입니다.")
        Boolean sensitiveAgreed,

        // 실제 동의 문서 버전
        @NotBlank(message = "동의 문서 버전은 필수입니다.")
        String consentDocVersion,

        // 특수 상황 JA 코드 목록
        // 미동의 시 null, 동의 후 해당 없음 선택 시 []
        List<@NotBlank String> sensitiveCodes

) {}