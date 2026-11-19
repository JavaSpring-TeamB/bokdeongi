package com.bokdeongi.backend.profile.service;

import com.bokdeongi.backend.profile.entity.MedianIncomeStandard;
import com.bokdeongi.backend.profile.repository.MedianIncomeStandardRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;

/**
 * 가구 소득을 기준 중위소득 대비 비율로 환산하는 서비스.
 */
@Service
public class IncomeCalculationService {

    private static final short MAX_STANDARD_HOUSEHOLD_SIZE = 7;

    // 2026년 8인 이상 가구는 7인 기준액에 1인당 959,198원 가산
    private static final int ADDITIONAL_AMOUNT_2026 = 959_198;
    private final MedianIncomeStandardRepository repository;

    public IncomeCalculationService(
            MedianIncomeStandardRepository repository
    ) {
        this.repository = repository;
    }

    /**
     * 연소득 또는 월소득을 기준 중위소득 비율(%)로 계산한다.
     */
    public BigDecimal calculateMedianIncomePct(
            Short householdSize,
            String incomeInputType,
            BigDecimal incomeAmount
    ) {
        if (householdSize == null || householdSize < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "가구원 수는 1명 이상이어야 합니다."
            );
        }

        if (incomeAmount == null || incomeAmount.signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "소득 금액을 확인해 주세요."
            );
        }

        BigDecimal monthlyIncome = switch (incomeInputType) {
            case "ANNUAL" -> incomeAmount.divide(
                    BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP
            );
            case "MONTHLY" -> incomeAmount;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "금액 입력 방식이 올바르지 않습니다."
            );
        };

        short baseYear = (short) Year.now().getValue();
        BigDecimal standardAmount =
                getMonthlyStandard(baseYear, householdSize);

        BigDecimal percentage = monthlyIncome
                .multiply(BigDecimal.valueOf(100))
                .divide(standardAmount, 1, RoundingMode.HALF_UP);

        // users.median_income_pct 컬럼의 DECIMAL(5,1) 범위 확인
        if (percentage.compareTo(new BigDecimal("9999.9")) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "소득 금액이 지나치게 큽니다. 입력값을 확인해 주세요."
            );
        }

        return percentage;
    }

    /**
     * 해당 연도의 가구원 수별 월 기준 중위소득을 조회한다.
     * 2026년 8인 이상 가구는 공식 고시의 가산 규칙을 적용한다.
     */
    private BigDecimal getMonthlyStandard(
            short year,
            short householdSize
    ) {
        // 8인 이상은 7인 가구의 기준액을 조회한다.
        short lookupSize = householdSize > MAX_STANDARD_HOUSEHOLD_SIZE
                ? MAX_STANDARD_HOUSEHOLD_SIZE
                : householdSize;

        MedianIncomeStandard standard = repository
                .findByYearAndHouseholdSize(year, lookupSize)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "해당 연도와 가구원 수의 기준 중위소득 자료가 없습니다."
                ));

        BigDecimal standardAmount =
                BigDecimal.valueOf(standard.getMonthlyAmount());

        if (householdSize > MAX_STANDARD_HOUSEHOLD_SIZE) {
            // 다른 연도의 가산 규칙을 2026년 기준으로 계산하지 않도록 방지
            if (year != 2026) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "해당 연도의 8인 이상 가구 가산 기준이 설정되지 않았습니다."
                );
            }

            int additionalPeople = householdSize - MAX_STANDARD_HOUSEHOLD_SIZE;

            BigDecimal additionalAmount = BigDecimal
                    .valueOf(ADDITIONAL_AMOUNT_2026)
                    .multiply(BigDecimal.valueOf(additionalPeople));

            standardAmount = standardAmount.add(additionalAmount);
        }

        return standardAmount;
    }

    /**
     * PROF-03: 지정한 연도의 기준 중위소득으로 소득 비율을 재계산한다.
     *
     * 연도 변경 시 기존 소득 금액을 새 기준표에 적용하기 위한 메서드.
     */
    public BigDecimal recalculateMedianIncomePct(
            Short householdSize,
            String incomeInputType,
            BigDecimal incomeAmount,
            short baseYear
    ) {
        if (householdSize == null || householdSize < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "가구원 수는 1명 이상이어야 합니다."
            );
        }

        if (incomeAmount == null || incomeAmount.signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "소득 금액을 확인해 주세요."
            );
        }

        BigDecimal monthlyIncome = switch (incomeInputType) {
            case "ANNUAL" -> incomeAmount.divide(
                    BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP
            );
            case "MONTHLY" -> incomeAmount;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "재계산할 수 없는 소득 입력 방식입니다."
            );
        };

        // 지정한 연도의 기준 중위소득 조회
        BigDecimal standardAmount = getMonthlyStandard(
                baseYear,
                householdSize
        );

        BigDecimal percentage = monthlyIncome
                .multiply(BigDecimal.valueOf(100))
                .divide(standardAmount, 1, RoundingMode.HALF_UP);

        if (percentage.compareTo(new BigDecimal("9999.9")) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "소득 금액이 지나치게 큽니다. 입력값을 확인해 주세요."
            );
        }

        return percentage;
    }

    /**
     * PROF-03: 지정한 연도의 기준 중위소득 데이터가
     * 1~7인 가구까지 모두 등록되어 있는지 확인한다.
     */
    public boolean isIncomeStandardReady(short year) {
        // 1~7인 가구의 기준표가 각각 존재해야 한다.
        for (short householdSize = 1; householdSize <= 7; householdSize++) {
            if (repository.findByYearAndHouseholdSize(year, householdSize)
                    .isEmpty()) {
                return false;
            }
        }

        return true;
    }
}