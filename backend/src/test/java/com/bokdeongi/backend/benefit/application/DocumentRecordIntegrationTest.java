package com.bokdeongi.backend.benefit.application;

import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.entity.UserConsent;
import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import com.bokdeongi.backend.auth.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DocumentRecordIntegrationTest {

    @Autowired
    private DocumentRecordService documentRecordService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserConsentRepository userConsentRepository;

    @Test
    void shouldSaveAndReadDocumentRecordFromDatabase() {

        User user = userRepository.saveAndFlush(
                new User(
                        "doc-record-test@example.com",
                        "test-password-hash",
                        "테스트",
                        "01000000000",
                        null
                )
        );

        Long userId = user.getId();

        // 실제 DOC_RECORD 동의 이력 생성
        userConsentRepository.saveAndFlush(
                new UserConsent(
                        user,
                        "DOC_RECORD",
                        "v1.0",
                        true
                )
        );

        // 실제 DB에 기록 저장
        boolean saved = documentRecordService.saveRecord(
                userId,
                268L,
                LocalDate.of(2026, 10, 30)
        );

        assertTrue(saved);

        // DB에서 다시 조회
        var records = documentRecordService.findRecords(userId);

        assertEquals(1, records.size());
        assertEquals(268L, records.get(0).documentTypeId());
        assertEquals(
                LocalDate.of(2026, 10, 30),
                records.get(0).issueDate()
        );
    }
}