package com.bokdeongi.backend.auth.repository;

import com.bokdeongi.backend.auth.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {
    /* 특정 회원의 약관 동의 기록 조회 */
    List<UserConsent> findByUserId(Long userId);
}
