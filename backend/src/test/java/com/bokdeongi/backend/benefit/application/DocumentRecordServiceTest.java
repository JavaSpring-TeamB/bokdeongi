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

class DocumentRecordServiceTest {

    @Test
    void shouldCalculateExpirationDate() {
        DocumentRecordService service =
                new DocumentRecordService(null, null);

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
                new DocumentRecordService(null, null);

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
                new DocumentRecordService(null, null);

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

        when(consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        1L, "DOC_RECORD"
                ))
                .thenReturn(Optional.empty());

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository
                );

        boolean saved = service.saveRecord(
                1L,
                268L,
                LocalDate.of(2026, 10, 30)
        );

        assertFalse(saved);
        verifyNoInteractions(recordRepository);
    }

    @Test
    void shouldSaveWithRecordConsent() {
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

        when(recordRepository.findRecords(1L))
                .thenReturn(null);

        when(recordRepository.saveRecords(
                eq(1L), anyString()
        )).thenReturn(1);

        DocumentRecordService service =
                new DocumentRecordService(
                        consentRepository,
                        recordRepository
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
                        recordRepository
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
                        recordRepository
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
                        recordRepository
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
                        recordRepository
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
                        recordRepository
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
                        recordRepository
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
}