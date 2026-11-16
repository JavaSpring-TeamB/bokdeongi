package com.bokdeongi.backend.auth.repository;

import com.bokdeongi.backend.auth.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    /* 특정 회원의 전체 약관 동의 이력 조회 */
    List<UserConsent> findByUserId(Long userId);

    /* 특정 회원의 약관 유형별 최신 동의 이력 조회 */
    Optional<UserConsent> findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
            Long userId,
            String consentType
    );
}