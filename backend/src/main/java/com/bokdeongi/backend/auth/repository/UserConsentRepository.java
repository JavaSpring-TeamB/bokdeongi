package com.bokdeongi.backend.auth.repository;

import com.bokdeongi.backend.auth.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 회원 약관 동의 이력 데이터 접근을 담당하는 Repository.
 *
 * 회원별 전체 동의 이력을 조회하거나,
 * 특정 약관 유형의 가장 최근 동의 상태를 확인한다.
 */
public interface UserConsentRepository
        extends JpaRepository<UserConsent, Long> {

    /**
     * 특정 회원의 전체 약관 동의 이력 조회.
     * 회원 ID에 연결된 모든 동의 및 철회 기록을 반환한다.
     */
    List<UserConsent> findByUserId(Long userId);

    /**
     * 특정 회원의 약관 유형별 최신 동의 이력 조회.
     *
     * actedAt을 내림차순으로 정렬하고,
     * 시간이 같으면 ID가 큰 기록을 우선한다.
     */
    Optional<UserConsent> findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
            Long userId,
            String consentType
    );
}