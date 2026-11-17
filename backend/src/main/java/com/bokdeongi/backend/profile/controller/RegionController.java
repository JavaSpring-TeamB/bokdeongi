package com.bokdeongi.backend.profile.controller;

import com.bokdeongi.backend.profile.dto.response.RegionResponse;
import com.bokdeongi.backend.profile.service.RegionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 지역 목록 조회 API.
 * 전국 시·도 및 시군구 선택에 필요한 데이터를 제공한다.
 */
@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionService regionService;

    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    /**
     * 전국 시·도 및 시군구 목록을 조회한다.
     */
    @GetMapping
    public List<RegionResponse> getRegions() {
        return regionService.getRegions();
    }
}