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

    /**
     * PROF-06: 추가 질문 답변 전체 조회.
     */
    @Transactional(readOnly = true)
    public List<ProfileAnswerResponse> getAnswers(Long userId) {
        User user = findUser(userId);
        JsonNode answers = readAnswers(user);

        List<ProfileAnswerResponse> responses = new ArrayList<>();

        for (Map.Entry<String, JsonNode> entry : answers.properties()) {
            JsonNode value = entry.getValue();

            // BEN-04에서 사용하는 문자열 답변만 조회한다.
            if (!value.isTextual()) {
                continue;
            }

            responses.add(new ProfileAnswerResponse(
                    entry.getKey(),
                    value.asText()
            ));
        }

        return responses;
    }

    /**
     * PROF-06: 특정 질문 답변 조회.
     */
    @Transactional(readOnly = true)
    public ProfileAnswerResponse getAnswer(Long userId, String key) {
        User user = findUser(userId);
        JsonNode answer = readAnswers(user).get(key);

        if (answer == null || !answer.isTextual()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "저장된 답변이 없습니다."
            );
        }

        return new ProfileAnswerResponse(
                key,
                answer.asText()
        );
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