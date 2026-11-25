package com.bokdeongi.backend.search.repository;

import com.bokdeongi.backend.search.entity.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 혜택 검색 기록의 저장 및 조회를 담당하는 Repository.
 */
@Repository
public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {
}