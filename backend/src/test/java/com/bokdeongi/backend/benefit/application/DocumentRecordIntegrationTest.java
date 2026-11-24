package com.bokdeongi.backend.benefit.application;

import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.entity.UserConsent;
import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import com.bokdeongi.backend.auth.repository.UserRepository;
import com.bokdeongi.backend.auth.service.AuthService;
import com.bokdeongi.backend.auth.dto.request.ConsentUpdateRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private AuthService authService;

    @Autowired
    private DocumentRecordRepository documentRecordRepository;

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

    @Test
    void shouldNotSaveDuplicateDocumentRecordInDatabase() {

        User user = userRepository.saveAndFlush(
                new User(
                        "doc-record-duplicate@example.com",
                        "test-password-hash",
                        "테스트",
                        "01000000001",
                        null
                )
        );

        Long userId = user.getId();

        userConsentRepository.saveAndFlush(
                new UserConsent(
                        user,
                        "DOC_RECORD",
                        "v1.0",
                        true
                )
        );

        LocalDate issueDate = LocalDate.of(2026, 10, 30);

        boolean firstSaved = documentRecordService.saveRecord(
                userId, 268L, issueDate
        );

        boolean secondSaved = documentRecordService.saveRecord(
                userId, 268L, issueDate
        );

        assertTrue(firstSaved);
        assertFalse(secondSaved);

        var records = documentRecordService.findRecords(userId);

        assertEquals(1, records.size());
    }

    @Test
    void shouldNotSaveDocumentRecordWithoutConsentInDatabase() {

        User user = userRepository.saveAndFlush(
                new User(
                        "doc-record-no-consent@example.com",
                        "test-password-hash",
                        "테스트",
                        "01000000002",
                        null
                )
        );

        Long userId = user.getId();

        boolean saved = documentRecordService.saveRecord(
                userId,
                268L,
                LocalDate.of(2026, 10, 30)
        );

        assertFalse(saved);

        assertTrue(
                documentRecordService.findRecords(userId).isEmpty()
        );
    }

    @Test
    void shouldDeleteRecordsWhenConsentIsWithdrawn() {

        User user = userRepository.saveAndFlush(
                new User(
                        "doc-record-withdraw@example.com",
                        "test-password-hash",
                        "테스트",
                        "01000000003",
                        null
                )
        );

        Long userId = user.getId();

        // 서류 기억 동의
        authService.updateConsent(
                userId,
                new ConsentUpdateRequest(
                        "DOC_RECORD",
                        "v1.0",
                        true
                )
        );

        LocalDate issueDate = LocalDate.of(2026, 10, 30);

        // 서류 기록 저장
        assertTrue(
                documentRecordService.saveRecord(
                        userId, 268L, issueDate
                )
        );

        // 실제 DB 저장 확인
        assertNotNull(
                documentRecordRepository.findRecords(userId)
        );

        // 서류 기억 동의 철회
        authService.updateConsent(
                userId,
                new ConsentUpdateRequest(
                        "DOC_RECORD",
                        "v1.0",
                        false
                )
        );

        // 동의 철회 확인
        assertFalse(
                documentRecordService.hasRecordConsent(userId)
        );

        // 실제 DB 기록 삭제 확인
        assertNull(
                documentRecordRepository.findRecords(userId)
        );

        // 철회 후 조회 제한 확인
        assertTrue(
                documentRecordService.findRecords(userId).isEmpty()
        );

        // 철회 후 재저장 방지 확인
        assertFalse(
                documentRecordService.saveRecord(
                        userId, 268L, issueDate
                )
        );

        // DB에 기록이 다시 생성되지 않았는지 확인
        assertNull(
                documentRecordRepository.findRecords(userId)
        );
    }

    @Test
    void shouldPreserveRecordsDuringConcurrentSave() throws Exception {

        User user = userRepository.saveAndFlush(
                new User(
                        "doc-record-concurrent@example.com",
                        "test-password-hash",
                        "테스트",
                        "01000000004",
                        null
                )
        );

        Long userId = user.getId();

        authService.updateConsent(
                userId,
                new ConsentUpdateRequest(
                        "DOC_RECORD",
                        "v1.0",
                        true
                )
        );

        LocalDate firstDate = LocalDate.of(2026, 10, 29);
        LocalDate secondDate = LocalDate.of(2026, 10, 30);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> {
                startLatch.await();

                return documentRecordService.saveRecord(
                        userId, 268L, firstDate
                );
            });

            Future<Boolean> second = executor.submit(() -> {
                startLatch.await();

                return documentRecordService.saveRecord(
                        userId, 268L, secondDate
                );
            });

            startLatch.countDown();

            assertTrue(first.get(10, TimeUnit.SECONDS));
            assertTrue(second.get(10, TimeUnit.SECONDS));

            var records =
                    documentRecordService.findRecords(userId);

            assertEquals(2, records.size());

            assertTrue(records.contains(
                    new DocumentRecordService.DocumentRecord(
                            268L, firstDate
                    )
            ));

            assertTrue(records.contains(
                    new DocumentRecordService.DocumentRecord(
                            268L, secondDate
                    )
            ));

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNotRetainRecordsDuringConcurrentWithdrawal()
            throws Exception {

        User user = userRepository.saveAndFlush(
                new User(
                        "doc-record-concurrent-withdraw@example.com",
                        "test-password-hash",
                        "테스트",
                        "01000000005",
                        null
                )
        );

        Long userId = user.getId();

        authService.updateConsent(
                userId,
                new ConsentUpdateRequest(
                        "DOC_RECORD",
                        "v1.0",
                        true
                )
        );

        // 기존 기록 저장
        assertTrue(documentRecordService.saveRecord(
                userId,
                268L,
                LocalDate.of(2026, 10, 29)
        ));

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            Future<Boolean> saveFuture = executor.submit(() -> {
                startLatch.await();

                return documentRecordService.saveRecord(
                        userId,
                        268L,
                        LocalDate.of(2026, 10, 30)
                );
            });

            Future<?> withdrawFuture = executor.submit(() -> {
                startLatch.await();

                authService.updateConsent(
                        userId,
                        new ConsentUpdateRequest(
                                "DOC_RECORD",
                                "v1.0",
                                false
                        )
                );

                return null;
            });

            startLatch.countDown();

            saveFuture.get(10, TimeUnit.SECONDS);
            withdrawFuture.get(10, TimeUnit.SECONDS);

            // 최종 동의 상태 확인
            assertFalse(
                    documentRecordService.hasRecordConsent(userId)
            );

            // 실제 DB 기록 삭제 확인
            assertNull(
                    documentRecordRepository.findRecords(userId)
            );

            // 조회 가능한 기록이 없는지 확인
            assertTrue(
                    documentRecordService.findRecords(userId)
                            .isEmpty()
            );

        } finally {
            executor.shutdownNow();
        }
    }
}