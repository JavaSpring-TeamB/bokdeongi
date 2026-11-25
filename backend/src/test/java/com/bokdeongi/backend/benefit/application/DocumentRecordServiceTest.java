package com.bokdeongi.backend.benefit.application;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import com.bokdeongi.backend.auth.entity.UserConsent;
import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import org.mockito.Mockito;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import com.bokdeongi.backend.benefit.entity.DocumentType;
import com.bokdeongi.backend.benefit.repository.DocumentTypeRepository;
import org.springframework.http.HttpStatus;

import java.time.ZoneId;

class DocumentRecordServiceTest {

    @Test
    void shouldCalculateExpirationDate() {
        DocumentRecordService service =
                new DocumentRecordService(null, null, null);

        var result = service.calculateValidity(
                LocalDate.of(2026, 10, 30),
                90
        );

        assertEquals(
                LocalDate.of(2027, 1, 28),
                result.expiresAt()
        );
    }

    @Test
    void shouldReturnNoExpirationWhenValidityIsNull() {
        DocumentRecordService service =
                new DocumentRecordService(null, null, null);

        var result = service.calculateValidity(
                LocalDate.of(2026, 10, 30),
                null
        );

        assertEquals(
                LocalDate.of(2026, 10, 30),
                result.issueDate()
        );

        assertNull(result.expiresAt());
        assertNull(result.valid());
    }

    @Test
    void shouldRejectNegativeValidityDays() {
        DocumentRecordService service =
                new DocumentRecordService(null, null, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculateValidity(
                        LocalDate.of(2026, 10, 30),
                        -1
                )
        );
    }

    @Test
    void shouldSerializeAndDeserializeDocumentRecord() throws Exception {

        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .findAndRegisterModules();

        var records = java.util.List.of(
                new DocumentRecordService.DocumentRecord(
                        268L,
                        LocalDate.of(2026, 10, 30)
                )
        );

        String json = mapper.writeValueAsString(records);

        var restored = mapper.readValue(
                json,
                new com.fasterxml.jackson.core.type.TypeReference<
                        java.util.List<DocumentRecordService.DocumentRecord>>() {}
        );

        assertEquals(records, restored);
    }


    @Test
    void shouldNotSaveWithoutRecordConsent() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        when(recordRepository.lockUser(1L))
                .thenReturn(true);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.empty());

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        null
                );

        boolean saved = service.saveRecord(
                1L,
                268L,
                LocalDate.of(2026, 10, 30)
        );

        assertFalse(saved);

        verify(recordRepository).lockUser(1L);

        verify(recordRepository, never())
                .findRecords(anyLong());

        verify(recordRepository, never())
                .saveRecords(anyLong(), anyString());
    }

    @Test
    void shouldSaveWithRecordConsent() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        when(recordRepository.lockUser(1L))
                .thenReturn(true);

        UserConsent consent = mock(UserConsent.class);
        when(consent.getAgreed()).thenReturn(true);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(consent));

        when(recordRepository.findRecords(1L))
                .thenReturn(null);

        when(recordRepository.saveRecords(
                eq(1L), anyString()
        )).thenReturn(1);

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        null
                );

        boolean saved = service.saveRecord(
                1L,
                268L,
                LocalDate.of(2026, 10, 30)
        );

        assertTrue(saved);
        verify(recordRepository).saveRecords(
                eq(1L),
                contains("268")
        );
    }

    @Test
    void shouldNotSaveDuplicateRecord() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        when(recordRepository.lockUser(1L))
                .thenReturn(true);

        UserConsent consent = mock(UserConsent.class);
        when(consent.getAgreed()).thenReturn(true);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(consent));

        when(recordRepository.findRecords(1L))
                .thenReturn(
                        "[{\"documentTypeId\":268,\"issueDate\":\"2026-10-30\"}]"
                );

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        null
                );

        boolean saved = service.saveRecord(
                1L,
                268L,
                LocalDate.of(2026, 10, 30)
        );

        assertFalse(saved);
        verify(recordRepository, never())
                .saveRecords(anyLong(), anyString());
    }

    @Test
    void shouldNotReadAfterConsentWithdrawal() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        UserConsent consent = mock(UserConsent.class);
        when(consent.getAgreed()).thenReturn(false);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(consent));

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        null
                );

        assertTrue(service.findRecords(1L).isEmpty());
        verifyNoInteractions(recordRepository);
    }


    @Test
    void shouldDeleteRecordsAfterConsentWithdrawal() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        UserConsent consent = mock(UserConsent.class);
        when(consent.getAgreed()).thenReturn(false);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(consent));

        when(recordRepository.deleteRecords(1L))
                .thenReturn(1);

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        null
                );

        assertTrue(service.deleteRecordsOnConsentWithdrawal(1L));

        verify(recordRepository).deleteRecords(1L);
    }

    @Test
    void shouldNotDeleteRecordsWhileConsentIsActive() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        UserConsent consent = mock(UserConsent.class);
        when(consent.getAgreed()).thenReturn(true);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(consent));

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        null
                );

        assertFalse(service.deleteRecordsOnConsentWithdrawal(1L));

        verifyNoInteractions(recordRepository);
    }

    @Test
    void shouldReturnEmptyRecordsWhenNoDataSaved() {
        Long userId = 1L;

        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        UserConsent consent = mock(UserConsent.class);
        when(consent.getAgreed()).thenReturn(true);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        userId, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(consent));

        when(recordRepository.findRecords(userId))
                .thenReturn(null);

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        null
                );

        var result = service.findRecords(userId);

        assertTrue(result.isEmpty());

        verify(recordRepository).findRecords(userId);
    }

    @Test
    void shouldFindRecordsByDocumentType() {
        Long userId = 1L;

        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        UserConsent consent = mock(UserConsent.class);
        when(consent.getAgreed()).thenReturn(true);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        userId, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(consent));

        when(recordRepository.findRecords(userId))
                .thenReturn("""
                    [
                      {"documentTypeId":268,"issueDate":"2026-10-30"},
                      {"documentTypeId":269,"issueDate":"2026-11-01"},
                      {"documentTypeId":268,"issueDate":"2026-11-10"}
                    ]
                    """);

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        null
                );

        var result = service.findRecordsByType(userId, 268L);

        assertEquals(2, result.size());

        assertEquals(268L, result.get(0).documentTypeId());
        assertEquals(LocalDate.of(2026, 10, 30),
                result.get(0).issueDate());

        assertEquals(268L, result.get(1).documentTypeId());
        assertEquals(LocalDate.of(2026, 11, 10),
                result.get(1).issueDate());

        verify(recordRepository).findRecords(userId);
    }

    @Test
    void shouldRejectRecordLookupWithoutConsent() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        DocumentTypeRepository documentTypeRepository =
                mock(DocumentTypeRepository.class);

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        documentTypeRepository
                );

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.empty());

        DocumentRecordException exception = assertThrows(
                DocumentRecordException.class,
                () -> service.getDocumentRecords(1L, null)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("CONSENT_REQUIRED", exception.getCode());
        assertEquals(
                "서류 유효기간 기억에 동의해야 합니다.",
                exception.getMessage()
        );

        verifyNoInteractions(recordRepository);
    }

    @Test
    void shouldRejectRecordRegistrationWithoutConsent() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        DocumentTypeRepository documentTypeRepository =
                mock(DocumentTypeRepository.class);

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        documentTypeRepository
                );

        when(recordRepository.lockUser(1L))
                .thenReturn(true);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.empty());

        DocumentRecordRequest request =
                new DocumentRecordRequest(
                        256L,
                        LocalDate.now(ZoneId.of("Asia/Seoul"))
                );

        DocumentRecordException exception = assertThrows(
                DocumentRecordException.class,
                () -> service.registerDocumentRecord(1L, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("CONSENT_REQUIRED", exception.getCode());
        assertEquals(
                "서류 유효기간 기억에 동의해야 합니다.",
                exception.getMessage()
        );

        verify(recordRepository, never())
                .saveRecords(anyLong(), anyString());

        verifyNoInteractions(documentTypeRepository);
    }

    @Test
    void shouldRegisterDocumentRecordWithConsent() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        DocumentTypeRepository documentTypeRepository =
                mock(DocumentTypeRepository.class);

        UserConsent consent = mock(UserConsent.class);

        when(consent.getAgreed()).thenReturn(true);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(consent));

        when(recordRepository.lockUser(1L))
                .thenReturn(true);

        when(documentTypeRepository.existsById(256L))
                .thenReturn(true);

        when(recordRepository.findRecords(1L))
                .thenReturn(null);

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        documentTypeRepository
                );

        DocumentRecordRequest request =
                new DocumentRecordRequest(
                        256L,
                        LocalDate.now(ZoneId.of("Asia/Seoul"))
                );

        assertDoesNotThrow(
                () -> service.registerDocumentRecord(1L, request)
        );

        verify(recordRepository)
                .saveRecords(eq(1L), anyString());
    }

    @Test
    void shouldReturnDocumentRecordsWithValidity() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        DocumentTypeRepository documentTypeRepository =
                mock(DocumentTypeRepository.class);

        UserConsent consent = mock(UserConsent.class);
        DocumentType documentType = mock(DocumentType.class);

        LocalDate issueDate =
                LocalDate.now(ZoneId.of("Asia/Seoul"));

        when(consent.getAgreed()).thenReturn(true);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(consent));

        when(recordRepository.findRecords(1L))
                .thenReturn(
                        "[{\"documentTypeId\":256,\"issueDate\":\""
                                + issueDate + "\"}]"
                );

        when(documentTypeRepository.findById(256L))
                .thenReturn(Optional.of(documentType));

        when(documentType.getName())
                .thenReturn("주민등록등본");

        when(documentType.getDefaultValidityDays())
                .thenReturn(90);

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        documentTypeRepository
                );

        DocumentRecordResponse response =
                service.getDocumentRecords(1L, null);

        assertEquals(1, response.records().size());

        DocumentRecordResponse.Item item =
                response.records().get(0);

        assertEquals(256L, item.documentTypeId());
        assertEquals("주민등록등본", item.documentName());
        assertEquals(issueDate, item.issueDate());
        assertEquals(90, item.validityDays());
        assertEquals(issueDate.plusDays(90), item.expiresAt());
        assertEquals(Boolean.TRUE, item.valid());
    }

    @Test
    void shouldRejectUnknownDocumentType() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        DocumentTypeRepository documentTypeRepository =
                mock(DocumentTypeRepository.class);

        UserConsent consent = mock(UserConsent.class);

        when(consent.getAgreed()).thenReturn(true);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(consent));

        when(recordRepository.lockUser(1L))
                .thenReturn(true);

        when(documentTypeRepository.existsById(999999L))
                .thenReturn(false);

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        documentTypeRepository
                );

        DocumentRecordRequest request =
                new DocumentRecordRequest(
                        999999L,
                        LocalDate.now(ZoneId.of("Asia/Seoul"))
                );

        DocumentRecordException exception = assertThrows(
                DocumentRecordException.class,
                () -> service.registerDocumentRecord(1L, request)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("DOCUMENT_TYPE_NOT_FOUND", exception.getCode());
        assertEquals(
                "존재하지 않는 서류 종류입니다.",
                exception.getMessage()
        );

        verify(recordRepository, never())
                .saveRecords(anyLong(), anyString());
    }

    @Test
    void shouldRejectFutureIssueDate() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        DocumentTypeRepository documentTypeRepository =
                mock(DocumentTypeRepository.class);

        UserConsent consent = mock(UserConsent.class);

        when(consent.getAgreed()).thenReturn(true);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(consent));

        when(recordRepository.lockUser(1L))
                .thenReturn(true);

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        documentTypeRepository
                );

        DocumentRecordRequest request =
                new DocumentRecordRequest(
                        256L,
                        LocalDate.now(ZoneId.of("Asia/Seoul"))
                                .plusDays(1)
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.registerDocumentRecord(1L, request)
        );

        verify(recordRepository, never())
                .saveRecords(anyLong(), anyString());

        verifyNoInteractions(documentTypeRepository);
    }

    @Test
    void shouldNotSaveRecordForMissingUser() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        DocumentTypeRepository documentTypeRepository =
                mock(DocumentTypeRepository.class);

        when(recordRepository.lockUser(1L))
                .thenReturn(false);

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        documentTypeRepository
                );

        boolean saved = service.saveRecord(
                1L,
                256L,
                LocalDate.now(ZoneId.of("Asia/Seoul"))
        );

        assertFalse(saved);

        verify(recordRepository).lockUser(1L);

        verify(recordRepository, never())
                .saveRecords(anyLong(), anyString());

        verifyNoInteractions(consentRepository);
    }

    @Test
    void shouldReturnRecordsFilteredByDocumentType() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        DocumentTypeRepository documentTypeRepository =
                mock(DocumentTypeRepository.class);

        UserConsent consent = mock(UserConsent.class);
        DocumentType documentType = mock(DocumentType.class);

        LocalDate issueDate =
                LocalDate.now(ZoneId.of("Asia/Seoul"));

        when(consent.getAgreed()).thenReturn(true);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(consent));

        when(recordRepository.findRecords(1L))
                .thenReturn(
                        "[{\"documentTypeId\":256,\"issueDate\":\""
                                + issueDate + "\"},"
                                + "{\"documentTypeId\":257,\"issueDate\":\""
                                + issueDate + "\"}]"
                );

        when(documentTypeRepository.findById(256L))
                .thenReturn(Optional.of(documentType));

        when(documentType.getName())
                .thenReturn("주민등록등본");

        when(documentType.getDefaultValidityDays())
                .thenReturn(90);

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        documentTypeRepository
                );

        DocumentRecordResponse response =
                service.getDocumentRecords(1L, 256L);

        assertEquals(1, response.records().size());

        assertEquals(
                256L,
                response.records().get(0).documentTypeId()
        );

        verify(documentTypeRepository, never())
                .findById(257L);
    }

    /**
     * 만료일 이전의 서류는 유효하다.
     */
    @Test
    void shouldReturnValidBeforeExpiration() {
        DocumentRecordService service =
                new DocumentRecordService(null, null, null);

        LocalDate today =
                LocalDate.now(ZoneId.of("Asia/Seoul"));

        var result = service.calculateValidity(
                today.minusDays(10),
                90
        );

        assertEquals(today.plusDays(80), result.expiresAt());
        assertEquals(Boolean.TRUE, result.valid());
    }

    /**
     * 만료일 당일까지 서류는 유효하다.
     */
    @Test
    void shouldRemainValidOnExpirationDate() {
        DocumentRecordService service =
                new DocumentRecordService(null, null, null);

        LocalDate today =
                LocalDate.now(ZoneId.of("Asia/Seoul"));

        var result = service.calculateValidity(
                today.minusDays(90),
                90
        );

        assertEquals(today, result.expiresAt());
        assertEquals(Boolean.TRUE, result.valid());
    }

    /**
     * 만료일이 지난 서류는 유효하지 않다.
     */
    @Test
    void shouldReturnInvalidAfterExpiration() {
        DocumentRecordService service =
                new DocumentRecordService(null, null, null);

        LocalDate today =
                LocalDate.now(ZoneId.of("Asia/Seoul"));

        var result = service.calculateValidity(
                today.minusDays(91),
                90
        );

        assertEquals(today.minusDays(1), result.expiresAt());
        assertEquals(Boolean.FALSE, result.valid());
    }

    /**
     * 발급일이 없으면 유효기간을 계산할 수 없다.
     */
    @Test
    void shouldRejectNullIssueDate() {
        DocumentRecordService service =
                new DocumentRecordService(null, null, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculateValidity(null, 90)
        );
    }

    /**
     * 내부 저장 메서드에서도 미래 발급일을 거부한다.
     */
    @Test
    void shouldRejectFutureIssueDateWhenSavingRecord() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        UserConsent consent = mock(UserConsent.class);

        when(recordRepository.lockUser(1L))
                .thenReturn(true);

        when(consent.getAgreed()).thenReturn(true);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(consent));

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        null
                );

        LocalDate futureDate =
                LocalDate.now(ZoneId.of("Asia/Seoul"))
                        .plusDays(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveRecord(
                        1L,
                        256L,
                        futureDate
                )
        );

        verify(recordRepository, never())
                .saveRecords(anyLong(), anyString());
    }

    /**
     * 내부 저장 메서드에서도 0 이하의 서류 ID를 거부한다.
     */
    @Test
    void shouldRejectInvalidDocumentTypeIdWhenSavingRecord() {
        UserConsentRepository consentRepository =
                mock(UserConsentRepository.class);

        DocumentRecordRepository recordRepository =
                mock(DocumentRecordRepository.class);

        UserConsent consent = mock(UserConsent.class);

        when(recordRepository.lockUser(1L))
                .thenReturn(true);

        when(consent.getAgreed()).thenReturn(true);

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.of(consent));

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository,
                        null
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveRecord(
                        1L,
                        0L,
                        LocalDate.now(ZoneId.of("Asia/Seoul"))
                )
        );

        verify(recordRepository, never())
                .saveRecords(anyLong(), anyString());
    }
}