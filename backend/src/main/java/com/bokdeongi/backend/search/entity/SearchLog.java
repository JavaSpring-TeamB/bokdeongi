package com.bokdeongi.backend.search.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 혜택 검색 기록을 관리하는 엔티티.
 *
 * 검색어, 전체 검색 결과 건수 및 검색 시각을 저장한다.
 */
@Entity
@Table(name = "search_logs")
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;

    @Column(name = "result_count", nullable = false)
    private Integer resultCount;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SearchLog() {
    }

    public SearchLog(String keyword, int resultCount) {
        this.keyword = keyword;
        this.resultCount = resultCount;
    }

    /**
     * 검색 기록 최초 저장 시 생성 및 검색 시각을 설정한다.
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        if (searchedAt == null) {
            searchedAt = now;
        }

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /**
     * 검색 기록 수정 시 갱신 시각을 설정한다.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public Long getId() {
        return id;
    }

    public String getKeyword() {
        return keyword;
    }

    public Integer getResultCount() {
        return resultCount;
    }

    public LocalDateTime getSearchedAt() {
        return searchedAt;
    }
}