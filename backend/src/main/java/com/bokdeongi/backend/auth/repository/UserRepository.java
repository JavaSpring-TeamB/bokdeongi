package com.bokdeongi.backend.auth.repository;

import com.bokdeongi.backend.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 회원 데이터 접근을 담당하는 Repository.
 *
 * JpaRepository를 상속하여 기본적인 CRUD 기능을 제공하고,
 * 이메일 및 카카오 ID를 이용한 회원 조회 기능을 정의한다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일 중복 여부 확인.
     * 회원가입 시 이미 사용 중인 이메일인지 검사한다.
     */
    boolean existsByEmail(String email);

    /**
     * 이메일로 회원 조회.
     * 일반 로그인 및 회원 정보 조회에 사용한다.
     */
    Optional<User> findByEmail(String email);

    /**
     * 이메일 로그인 처리 시 회원 행에 쓰기 잠금을 건다.
     * 동시에 여러 로그인 요청이 들어와도 실패 횟수를 순서대로 갱신한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    /**
     * 카카오 고유 ID로 회원 조회.
     * 카카오 로그인 시 기존 회원 여부를 확인한다.
     */
    Optional<User> findByKakaoId(String kakaoId);
}