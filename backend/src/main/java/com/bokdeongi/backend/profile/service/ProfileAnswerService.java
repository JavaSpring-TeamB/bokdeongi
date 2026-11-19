package com.bokdeongi.backend.profile.service;

import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.repository.UserRepository;
import com.bokdeongi.backend.profile.dto.response.ProfileAnswerResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.Clock;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ProfileAnswerService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ProfileAnswerService(
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    private static final int RECONFIRM_DAYS = 30;

    // 재확인 대상 질문 키는 정책 확정 후 등록한다.
    private static final Set<String> RECONFIRM_KEYS = Set.of();

    private final Clock clock = Clock.systemDefaultZone();

    @Transactional(readOnly = true)
    public List<ProfileAnswerResponse> getAnswers(Long userId) {
        User user = findUser(userId);
        JsonNode answers = readAnswers(user);

        List<ProfileAnswerResponse> responses = new ArrayList<>();

        for (Map.Entry<String, JsonNode> entry : answers.properties()) {
            ProfileAnswerResponse response =
                    toResponse(entry.getKey(), entry.getValue());

            if (response != null) {
                responses.add(response);
            }
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public ProfileAnswerResponse getAnswer(Long userId, String key) {
        User user = findUser(userId);
        JsonNode answer = readAnswers(user).get(key);

        ProfileAnswerResponse response = toResponse(key, answer);

        if (response == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "저장된 답변이 없습니다."
            );
        }

        return response;
    }

    private ProfileAnswerResponse toResponse(String key, JsonNode answer) {
        if (answer == null) {
            return null;
        }

        String value;
        LocalDateTime answeredAt = null;

        // 기존 BEN-04 문자열 형식
        if (answer.isTextual()) {
            value = answer.asText();
        }
        // 테이블 명세서의 객체 형식
        else if (answer.isObject()) {
            JsonNode valueNode = answer.get("value");

            if (valueNode == null || !valueNode.isTextual()) {
                return null;
            }

            value = valueNode.asText();

            JsonNode answeredAtNode = answer.get("answeredAt");

            if (answeredAtNode != null && answeredAtNode.isTextual()) {
                try {
                    answeredAt = LocalDateTime.parse(
                            answeredAtNode.asText()
                    );
                } catch (DateTimeParseException e) {
                    throw new IllegalStateException(
                            "답변 확인 시각 형식이 올바르지 않습니다.", e
                    );
                }
            }
        } else {
            return null;
        }

        // 재확인 대상으로 등록된 질문에만 30일 만료 정책을 적용한다.
        boolean needsReconfirmation = needsReconfirmation(key, answeredAt);

        return new ProfileAnswerResponse(
                key,
                value,
                answeredAt,
                needsReconfirmation
        );
    }

    private boolean needsReconfirmation(
            String key,
            LocalDateTime answeredAt
    ) {
        if (!RECONFIRM_KEYS.contains(key)) {
            return false;
        }

        // 기존 문자열 답변은 확인 시각이 없으므로 재확인이 필요하다.
        if (answeredAt == null) {
            return true;
        }

        LocalDateTime expiresAt = answeredAt.plusDays(RECONFIRM_DAYS);
        LocalDateTime now = LocalDateTime.now(clock);

        return !now.isBefore(expiresAt);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회원을 찾을 수 없습니다."
                ));
    }

    private JsonNode readAnswers(User user) {
        String json = user.getAnswers();

        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            JsonNode node = objectMapper.readTree(json);

            if (!node.isObject()) {
                throw new IllegalStateException(
                        "추가 질문 데이터 형식이 올바르지 않습니다."
                );
            }

            return node;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "추가 질문 데이터를 읽을 수 없습니다.", e
            );
        }
    }
}