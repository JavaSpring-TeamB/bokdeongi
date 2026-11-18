package com.bokdeongi.backend.profile.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 연도 및 가구원 수별 기준 중위소득을 관리하는 Entity.
 */
@Entity
@Table(name = "median_income_standards")
@IdClass(MedianIncomeStandard.MedianIncomeStandardId.class)
public class MedianIncomeStandard {

    @Id
    @Column(name = "year", nullable = false)
    private Short year;

    @Id
    @Column(name = "household_size", nullable = false)
    private Short householdSize;

    @Column(name = "monthly_amount", nullable = false)
    private Integer monthlyAmount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected MedianIncomeStandard() {
    }

    public Short getYear() {
        return year;
    }

    public Short getHouseholdSize() {
        return householdSize;
    }

    public Integer getMonthlyAmount() {
        return monthlyAmount;
    }

    /**
     * 연도와 가구원 수로 기준 중위소득을 식별하는 복합키.
     */
    public static class MedianIncomeStandardId implements Serializable {

        private Short year;
        private Short householdSize;

        public MedianIncomeStandardId() {
        }

        public MedianIncomeStandardId(Short year, Short householdSize) {
            this.year = year;
            this.householdSize = householdSize;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MedianIncomeStandardId that)) return false;
            return Objects.equals(year, that.year)
                    && Objects.equals(householdSize, that.householdSize);
        }

        @Override
        public int hashCode() {
            return Objects.hash(year, householdSize);
        }
    }
}