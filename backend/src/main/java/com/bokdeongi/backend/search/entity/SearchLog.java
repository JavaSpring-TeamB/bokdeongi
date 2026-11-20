package com.bokdeongi.backend.search.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

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

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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