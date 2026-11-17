package com.bokdeongi.backend.profile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 지역 정보 엔티티.
 * regions 테이블의 시·도 및 시군구 정보를 조회한다.
 */
@Entity
@Table(name = "regions")
public class Region {

    @Id
    @Column(name = "region_code", length = 10, nullable = false)
    private String regionCode;

    @Column(name = "sido_name", length = 20, nullable = false)
    private String sidoName;

    @Column(name = "sigungu_name", length = 20)
    private String sigunguName;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    protected Region() {
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getSidoName() {
        return sidoName;
    }

    public String getSigunguName() {
        return sigunguName;
    }

    public Short getSortOrder() {
        return sortOrder;
    }
}