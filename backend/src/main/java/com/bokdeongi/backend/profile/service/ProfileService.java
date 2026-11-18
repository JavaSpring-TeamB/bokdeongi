package com.bokdeongi.backend.profile.service;

import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.entity.UserConsent;
import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import com.bokdeongi.backend.auth.repository.UserRepository;
import com.bokdeongi.backend.common.crypto.FieldEncryptionService;
import com.bokdeongi.backend.eligibility.service.ProfileChangedEvent;
import com.bokdeongi.backend.profile.dto.request.ProfileStep1Request;
import com.bokdeongi.backend.profile.dto.request.ProfileStep2Request;
import com.bokdeongi.backend.profile.dto.request.ProfileStep3Request;
import com.bokdeongi.backend.profile.dto.request.ProfileStep4Request;
import com.bokdeongi.backend.profile.dto.response.ProfileResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bokdeongi.backend.profile.entity.Region;
import com.bokdeongi.backend.profile.repository.RegionRepository;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Set;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final FieldEncryptionService fieldEncryptionService;
    private final UserConsentRepository userConsentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final String sensitiveConsentVersion;
    private final RegionRepository regionRepository;
    private final IncomeCalculationService incomeCalculationService;

    public ProfileService(
            UserRepository userRepository,
            RegionRepository regionRepository,
            ObjectMapper objectMapper,
            FieldEncryptionService fieldEncryptionService,
            UserConsentRepository userConsentRepository,
            ApplicationEventPublisher applicationEventPublisher,
            @Value("${app.consent.sensitive-version}") String sensitiveConsentVersion,
            IncomeCalculationService incomeCalculationService
    ) {
        this.userRepository = userRepository;
        this.regionRepository = regionRepository;
        this.objectMapper = objectMapper;
        this.fieldEncryptionService = fieldEncryptionService;
        this.userConsentRepository = userConsentRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.sensitiveConsentVersion = sensitiveConsentVersion;
        this.incomeCalculationService = incomeCalculationService;
    }

    /**
     * 로그인한 회원의 프로필 조회.
     */

    @Transactional
    public ProfileResponse getProfile(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회원을 찾을 수 없습니다."
                ));

        // PROF-03: 연도가 변경되면 최신 기준 중위소득으로 재계산
        String incomeType = user.getIncomeInputType();
        short currentYear = (short) Year.now().getValue();

        boolean needsRecalculation =
                ("ANNUAL".equals(incomeType) || "MONTHLY".equals(incomeType))
                        && user.getIncomeBaseYear() != null
                        && user.getIncomeBaseYear() != currentYear;

        if (needsRecalculation
                && incomeCalculationService.isIncomeStandardReady(currentYear)) {

            // 8인 이상 가구는 현재 2026년 가산 규칙만 지원한다.
            // 다른 연도는 기준표가 있어도 재계산을 건너뛴다.
            boolean unsupportedAdditionalRule =
                    user.getHouseholdSize() != null
                            && user.getHouseholdSize() > 7
                            && currentYear != 2026;

            if (!unsupportedAdditionalRule) {
                recalculateIncomeForYear(userId, currentYear);
            }
        }

        return new ProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhoneNumber(),

                // 1단계
                user.getBirthDate(),
                user.getGender(),
                user.getRegionCode(),
                user.getSidoName(),
                user.getSigunguName(),

                // 2단계
                user.getHouseholdSize(),
                user.getIncomeInputType(),
                user.getIncomeBaseYear(),
                user.getMedianIncomePct(),
                user.getHousingType(),
                user.getHasHouse(),
                parseCodes(user.getHouseholdTypeCodes()),

                // 3단계
                parseCodes(user.getStatusCodes()),
                user.getMaritalStatus(),
                user.getChildrenCount(),
                user.getYoungestChildBirthYear(),
                user.getWelfareStatus(),

                // 진행 상태
                user.getProfileVersion(),
                user.getProfileStep(),
                user.getProfileCompletedAt() != null,
                user.getProfileCompletedAt()
        );
    }

    /**
     * PROF-01: 프로필 1단계 기본 정보 저장.
     */
    @Transactional
    public ProfileResponse saveProfileStep1(
            Long userId,
            ProfileStep1Request request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회원을 찾을 수 없습니다."
                ));

        Region region = regionRepository.findById(request.regionCode())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "존재하지 않는 지역 코드입니다."
                ));

        // 시·도 대표 코드는 선택할 수 없으며, 세종특별자치시는 예외로 허용한다.
        if (region.getSigunguName() == null
                && !"36110".equals(region.getRegionCode())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "시군구까지 선택해야 합니다."
            );
        }

        user.updateProfileStep1(
                request.birthDate(),
                request.gender(),
                region.getRegionCode(),
                region.getSidoName(),
                region.getSigunguName()
        );

        // 프로필 변경 이벤트 발행
        applicationEventPublisher.publishEvent(
                new ProfileChangedEvent(userId)
        );

        return getProfile(userId);
    }

    /**
     * PROF-01: 프로필 2단계 가구 정보 저장.
     */
    @Transactional
    public ProfileResponse saveProfileStep2(
            Long userId,
            ProfileStep2Request request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회원을 찾을 수 없습니다."
                ));

        if (user.getProfileStep() == null
                || user.getProfileStep() < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "프로필 1단계를 먼저 완료해야 합니다."
            );
        }

        String incomeType = request.incomeInputType();
        BigDecimal incomeAmount = request.incomeAmount();
        BigDecimal medianIncomePct = request.medianIncomePct();

        switch (incomeType) {
            case "ANNUAL", "MONTHLY" -> {
                if (incomeAmount == null || medianIncomePct != null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "금액 입력 시 소득 금액만 입력해야 합니다."
                    );
                }

                // 입력한 소득을 기준 중위소득 대비 비율로 자동 환산한다.
                medianIncomePct = incomeCalculationService.calculateMedianIncomePct(
                        request.householdSize(),
                        incomeType,
                        incomeAmount
                );
            }

            case "BRACKET" -> {
                if (medianIncomePct == null || incomeAmount != null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "소득 구간 입력 시 중위소득 비율만 입력해야 합니다."
                    );
                }

                if (medianIncomePct.compareTo(new BigDecimal("9999.9")) > 0) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "중위소득 비율을 확인해 주세요."
                    );
                }
            }

            case "UNKNOWN" -> {
                if (incomeAmount != null || medianIncomePct != null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "소득 모름 선택 시 금액과 비율은 입력할 수 없습니다."
                    );
                }
            }

            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 소득 입력 방식입니다."
            );
        }

        // 가구 유형 JA 코드 중복 검증
        if (request.householdTypeCodes().size()
                != Set.copyOf(request.householdTypeCodes()).size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "가구 유형 코드는 중복 선택할 수 없습니다."
            );
        }

        String householdTypeCodesJson;

        try {
            householdTypeCodesJson =
                    objectMapper.writeValueAsString(
                            request.householdTypeCodes()
                    );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "가구 유형 변환에 실패했습니다.", e
            );
        }

        // 소득을 모르는 경우에는 기준 연도를 기록하지 않는다.
        Short incomeBaseYear = "UNKNOWN".equals(incomeType)
                ? null
                : (short) Year.now().getValue();

        user.updateProfileStep2(
                request.householdSize(),
                incomeType,
                incomeAmount,
                incomeBaseYear,
                medianIncomePct,
                request.housingType(),
                request.noHouse(),
                householdTypeCodesJson
        );

        // 프로필 변경 이벤트 발행
        applicationEventPublisher.publishEvent(
                new ProfileChangedEvent(userId)
        );

        return getProfile(userId);
    }

    /**
     * PROF-03: 새로운 연도의 기준 중위소득으로 기존 소득 비율을 재계산한다.
     */
    @Transactional
    public void recalculateIncomeForYear(Long userId, short targetYear) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회원을 찾을 수 없습니다."
                ));

        String incomeType = user.getIncomeInputType();

        // UNKNOWN, BRACKET 사용자는 자동 재계산 대상이 아니다.
        if (!"ANNUAL".equals(incomeType)
                && !"MONTHLY".equals(incomeType)) {
            return;
        }

        // 이미 해당 연도로 계산했다면 중복 재계산하지 않는다.
        if (user.getIncomeBaseYear() != null
                && user.getIncomeBaseYear() == targetYear) {
            return;
        }

        // 재계산 대상 사용자에 대해서만 기준표 준비 여부를 확인한다.
        if (!incomeCalculationService.isIncomeStandardReady(targetYear)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "해당 연도의 기준 중위소득 자료가 준비되지 않았습니다."
            );
        }

        // 새로운 연도의 기준 중위소득으로 재계산한다.
        BigDecimal recalculatedPct =
                incomeCalculationService.recalculateMedianIncomePct(
                        user.getHouseholdSize(),
                        incomeType,
                        user.getIncomeAmount(),
                        targetYear
                );

        user.updateIncomeCalculation(targetYear, recalculatedPct);

        applicationEventPublisher.publishEvent(
                new ProfileChangedEvent(userId)
        );
    }

    /**
     * PROF-01: 프로필 3단계 나의 상황 정보 저장.
     */
    @Transactional
    public ProfileResponse saveProfileStep3(
            Long userId,
            ProfileStep3Request request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회원을 찾을 수 없습니다."
                ));

        if (user.getProfileStep() == null
                || user.getProfileStep() < 2) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "프로필 2단계를 먼저 완료해야 합니다."
            );
        }

        // 자녀 수와 막내 자녀 출생 연도 검증
        if (request.childrenCount() == 0
                && request.youngestChildBirthYear() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "자녀가 없으면 막내 자녀 출생 연도를 입력할 수 없습니다."
            );
        }

        if (request.childrenCount() > 0) {
            Short birthYear = request.youngestChildBirthYear();

            if (birthYear == null
                    || birthYear < 1900
                    || birthYear > Year.now().getValue()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "막내 자녀의 출생 연도를 확인해 주세요."
                );
            }

            if (user.getBirthDate() != null
                    && birthYear < user.getBirthDate().getYear()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "막내 자녀의 출생 연도는 회원 출생 연도보다 빠를 수 없습니다."
                );
            }
        }

        // 현재 상황 JA 코드 검증
        Set<String> allowedStatusCodes = Set.of(
                "JA0313", "JA0314", "JA0315", "JA0316",
                "JA0317", "JA0318", "JA0319", "JA0320",
                "JA0326", "JA0327", "SELF_EMPLOYED"
        );

        if (request.statusCodes().stream()
                .anyMatch(code -> !allowedStatusCodes.contains(code))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 현재 상황 코드가 포함되어 있습니다."
            );
        }

        if (request.statusCodes().size()
                != Set.copyOf(request.statusCodes()).size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "현재 상황 코드는 중복 선택할 수 없습니다."
            );
        }

        String statusCodesJson;

        try {
            statusCodesJson =
                    objectMapper.writeValueAsString(request.statusCodes());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "현재 상황 코드 변환에 실패했습니다.", e
            );
        }

        user.updateProfileStep3(
                statusCodesJson,
                request.maritalStatus(),
                request.childrenCount(),
                request.youngestChildBirthYear(),
                request.welfareStatus()
        );

        // 프로필 변경 이벤트 발행
        applicationEventPublisher.publishEvent(
                new ProfileChangedEvent(userId)
        );

        return getProfile(userId);
    }

    /**
     * PROF-01: 프로필 4단계 특수 상황 저장.
     */
    @Transactional
    public ProfileResponse saveProfileStep4(
            Long userId,
            ProfileStep4Request request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회원을 찾을 수 없습니다."
                ));

        if (user.getProfileStep() == null
                || user.getProfileStep() < 3) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "프로필 3단계를 먼저 완료해야 합니다."
            );
        }

        String currentDocVersion = sensitiveConsentVersion;

        if (!currentDocVersion.equals(request.consentDocVersion())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "동의 문서 버전이 일치하지 않습니다."
            );
        }

        byte[] encryptedSensitiveCodes = null;

        if (request.sensitiveAgreed()) {
            if (request.sensitiveCodes() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "동의한 경우 특수 상황 목록을 입력해야 합니다."
                );
            }

            Set<String> allowedSensitiveCodes = Set.of(
                    "JA0301", "JA0302", "JA0303",
                    "JA0328", "JA0329", "JA0330",
                    "JA0401", "JA0402"
            );

            if (request.sensitiveCodes().stream()
                    .anyMatch(code -> !allowedSensitiveCodes.contains(code))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "올바르지 않은 특수 상황 코드가 포함되어 있습니다."
                );
            }

            if (request.sensitiveCodes().size()
                    != Set.copyOf(request.sensitiveCodes()).size()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "특수 상황 코드는 중복 선택할 수 없습니다."
                );
            }

            String sensitiveCodesJson;

            try {
                sensitiveCodesJson =
                        objectMapper.writeValueAsString(
                                request.sensitiveCodes()
                        );
            } catch (Exception e) {
                throw new IllegalStateException(
                        "특수 상황 코드 변환에 실패했습니다.", e
                );
            }

            // AES-256-GCM 암호화
            encryptedSensitiveCodes =
                    fieldEncryptionService.encrypt(sensitiveCodesJson);

        } else {
            if (request.sensitiveCodes() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "동의하지 않은 경우 특수 상황 정보를 입력할 수 없습니다."
                );
            }
        }

        // 동의 또는 미동의 이력 추가
        userConsentRepository.save(
                new UserConsent(
                        user,
                        "SENSITIVE",
                        currentDocVersion,
                        request.sensitiveAgreed()
                )
        );

        // 미동의 시 기존 민감정보 제거
        user.updateProfileStep4(encryptedSensitiveCodes);

        // 프로필 변경 이벤트 발행
        applicationEventPublisher.publishEvent(
                new ProfileChangedEvent(userId)
        );

        return getProfile(userId);
    }

    /**
     * PROF-01: 프로필 최종 완료 처리.
     * 4단계는 선택 사항이다.
     */
    @Transactional
    public ProfileResponse completeProfile(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회원을 찾을 수 없습니다."
                ));

        if (user.getProfileStep() == null
                || user.getProfileStep() < 3) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "프로필 1~3단계를 먼저 완료해야 합니다."
            );
        }

        user.completeProfile();

        return getProfile(userId);
    }

    /**
     * DB의 JSON 코드 목록을 List로 변환.
     * NULL은 미입력, []는 해당 없음으로 구분한다.
     */
    private List<String> parseCodes(String json) {

        if (json == null) {
            return null;
        }

        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<List<String>>() {}
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "프로필 코드 목록을 읽을 수 없습니다.",
                    e
            );
        }
    }
}