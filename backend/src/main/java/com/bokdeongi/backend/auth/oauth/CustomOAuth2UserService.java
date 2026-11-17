package com.bokdeongi.backend.auth.oauth;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * 카카오 OAuth2 사용자 정보 조회 서비스.
 *
 * 카카오에서 사용자 정보를 조회하고 고유 ID가 존재하는지 검증한다.
 * 신규 회원과 기존 회원의 처리는 OAuth2LoginSuccessHandler에서 담당한다.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    /**
     * 카카오 사용자 정보를 조회하고 필수 식별자를 검증한다.
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {

        // 카카오 사용자 정보 조회
        OAuth2User oauth2User = super.loadUser(userRequest);

        // 카카오 고유 ID 확인
        Object kakaoId = oauth2User.getAttribute("id");

        if (kakaoId == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_kakao_id"),
                    "카카오 사용자 ID가 없습니다."
            );
        }

        // 회원가입 또는 로그인 처리를 위해 사용자 정보 반환
        return oauth2User;
    }
}