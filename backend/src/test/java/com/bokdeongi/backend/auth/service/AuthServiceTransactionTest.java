package com.bokdeongi.backend.auth.service;

import com.bokdeongi.backend.auth.dto.request.ConsentUpdateRequest;
import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.entity.UserConsent;
import com.bokdeongi.backend.auth.repository.UserRepository;
import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import com.bokdeongi.backend.benefit.application.DocumentRecordRepository;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTransactionTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private UserConsentRepository userConsentRepository;

    @MockitoSpyBean
    private DocumentRecordRepository documentRecordRepository;

    // 테스트 환경에서는 실제 이메일을 발송하지 않는다.
    @MockitoBean
    private JavaMailSender javaMailSender;

    @Test
    void shouldRollbackDocumentDeletionWhenConsentSaveFails() throws JsonProcessingException {
        // 1. 실제 H2 DB에 회원 생성
        User user = userRepository.saveAndFlush(
                new User(
                        "rollback-test@example.com",
                        "test-password-hash",
                        "테스트",
                        "01000000000",
                        null
                )
        );

        Long userId = user.getId();

        // 2. 서류 기록 저장
        String originalJson =
                "[{\"documentTypeId\":1,\"issueDate\":\"2026-11-01\"}]";

        jdbcTemplate.update(
                "UPDATE users SET doc_records = ? WHERE id = ?",
                originalJson,
                userId
        );

        // 3. 동의 이력 저장 시 예외 발생
        doThrow(new RuntimeException("동의 이력 저장 실패"))
                .when(userConsentRepository)
                .save(any(UserConsent.class));

        // 4. 실제 AuthService 동의 철회 실행
        assertThrows(RuntimeException.class, () ->
                authService.updateConsent(
                        userId,
                        new ConsentUpdateRequest(
                                "DOC_RECORD",
                                "v1.0",
                                false
                        )
                )
        );

        verify(documentRecordRepository, times(1))
                .deleteRecords(userId);

        // 5. 롤백 후 서류 기록 유지 확인
        String actualJson = jdbcTemplate.queryForObject(
                "SELECT doc_records FROM users WHERE id = ?",
                String.class,
                userId
        );

        ObjectMapper objectMapper = new ObjectMapper();

        JsonNode actualNode = objectMapper.readTree(actualJson);

        if (actualNode.isTextual()) {
            actualNode = objectMapper.readTree(actualNode.asText());
        }

        JsonNode expectedNode = objectMapper.readTree(originalJson);

        assertEquals(expectedNode, actualNode);

        // 6. 철회 이력도 저장되지 않았는지 확인
        assertTrue(
                userConsentRepository
                        .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                                userId, "DOC_RECORD"
                        )
                        .isEmpty()
        );
    }
}