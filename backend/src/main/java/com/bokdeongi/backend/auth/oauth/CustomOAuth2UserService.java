package com.bokdeongi.backend.auth.oauth;

import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
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

        // 4. 기존 카카오 회원 조회
        if (userRepository.findByKakaoId(kakaoId).isEmpty()) {

            // 5. 기존 이메일 회원과 중복되는지 확인
            if (email != null && userRepository.existsByEmail(email)) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("email_already_exists"),
                        "이미 가입된 이메일입니다."
                );
            }

            // 6. 신규 카카오 회원 저장
            User newUser = User.createKakaoUser(
                    kakaoId,
                    nickname,
                    email
            );

            userRepository.save(newUser);
        }

        // 7. 카카오 사용자 정보 반환
        return oauth2User;
    }
}
