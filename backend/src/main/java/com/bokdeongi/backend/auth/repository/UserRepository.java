package com.bokdeongi.backend.auth.repository;

import com.bokdeongi.backend.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /* 이메일 중복 확인 */
    boolean existsByEmail(String email);

    /* 이메일로 회원 조회 */
    Optional<User> findByEmail(String email);

    /* 카카오 ID로 회원 조회 */
    Optional<User> findByKakaoId(String kakaoId);
}