package com.bokdeongi.backend.auth.service;

import com.bokdeongi.backend.auth.dto.request.ConsentRequest;
import com.bokdeongi.backend.auth.dto.request.SignupRequest;
import com.bokdeongi.backend.auth.dto.response.SignupResponse;
import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.entity.UserConsent;
import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import com.bokdeongi.backend.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {

        // 1. 이메일 중복 확인
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 2. 약관 유형 및 중복 확인
        Set<String> allowedConsents = Set.of(
                "TERMS",
                "PRIVACY",
                "OPTIONAL_INFO",
                "SENSITIVE",
                "DOC_RECORD"
        );

        Set<String> consentTypes = new HashSet<>();

        for (ConsentRequest consent : request.consents()) {
            if (!allowedConsents.contains(consent.consentType())) {
                throw new IllegalArgumentException(
                        "유효하지 않은 약관 유형입니다: " + consent.consentType()
                );
            }

            if (!consentTypes.add(consent.consentType())) {
                throw new IllegalArgumentException(
                        "중복된 약관 동의 항목입니다."
                );
            }
        }
        
        // 3. 필수 약관 동의 검증
        Set<String> requiredConsents = Set.of(
                "TERMS",
                "PRIVACY"
        );

        for (String requiredType : requiredConsents) {
            boolean agreed = request.consents().stream()
                    .anyMatch(consent ->
                            requiredType.equals(consent.consentType())
                                    && consent.agreed()
                    );

            if (!agreed) {
                throw new IllegalArgumentException(
                        "필수 약관에 동의해야 합니다: " + requiredType
                );
            }
        }

        // 4. 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        // 5. 사용자 생성
        User user = new User(
                request.email(),
                encodedPassword,
                request.name(),
                request.phoneNumber(),
                request.birthDate()
        );

        // 6. 사용자 저장
        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시 가입 요청에 대한 DB UNIQUE 제약 보호
            throw new IllegalArgumentException("회원가입 정보를 확인해주세요.", e);
        }

        // 7. 약관 동의 이력 저장
        for (ConsentRequest consent : request.consents()) {
            UserConsent userConsent = new UserConsent(
                    savedUser,
                    consent.consentType(),
                    consent.docVersion(),
                    consent.agreed()
            );

            userConsentRepository.save(userConsent);
        }

        // 8. 회원가입 결과 반환
        return new SignupResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                "회원가입이 완료되었습니다."
        );
    }
}