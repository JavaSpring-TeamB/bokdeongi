package com.bokdeongi.backend.auth.oauth;

import com.bokdeongi.backend.auth.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {

        // 1. 카카오 사용자 정보 조회
        OAuth2User oauth2User = super.loadUser(userRequest);

        // 2. 카카오 고유 ID 추출
        Object kakaoIdValue = oauth2User.getAttribute("id");

        if (kakaoIdValue == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_kakao_id"),
                    "카카오 사용자 ID가 없습니다."
            );
        }

        String kakaoId = kakaoIdValue.toString();

        // 3. 카카오 계정 정보 추출
        Map<String, Object> kakaoAccount =
                oauth2User.getAttribute("kakao_account");

        String email = null;
        String nickname = null;

        if (kakaoAccount != null) {
            Object emailValue = kakaoAccount.get("email");

            if (emailValue instanceof String) {
                email = (String) emailValue;
            }

            Object profileValue = kakaoAccount.get("profile");

            if (profileValue instanceof Map<?, ?> profile) {
                Object nicknameValue = profile.get("nickname");

                if (nicknameValue instanceof String) {
                    nickname = (String) nicknameValue;
                }
            }
        }

        /**
         * 신규 카카오 회원은 약관 동의 완료 후 DB에 저장
         * 기존 회원과 신규 회원의 분기는 OAuth2LoginSuccessHandler에서 처리
          */

        // 7. 카카오 사용자 정보 반환
        return oauth2User;
    }
}
