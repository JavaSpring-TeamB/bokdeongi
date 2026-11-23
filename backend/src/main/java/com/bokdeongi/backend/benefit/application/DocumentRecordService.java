package com.bokdeongi.backend.benefit.application;

import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentRecordService {

    private static final String DOC_RECORD = "DOC_RECORD";

    private final UserConsentRepository consentRepository;
    private final DocumentRecordRepository recordRepository;
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    public DocumentRecordService(
            UserConsentRepository consentRepository,
            DocumentRecordRepository recordRepository
    ) {
        this.consentRepository = consentRepository;
        this.recordRepository = recordRepository;
    }

    public boolean hasRecordConsent(Long userId) {
        return consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        userId, DOC_RECORD
                )
                .map(consent -> Boolean.TRUE.equals(consent.getAgreed()))
                .orElse(false);
    }

    public boolean saveRecord(
            Long userId,
            Long documentTypeId,
            LocalDate issueDate
    ) {
        if (userId == null || documentTypeId == null
                || issueDate == null) {
            throw new IllegalArgumentException(
                    "서류 기록에 필요한 값이 없습니다."
            );
        }

        if (!hasRecordConsent(userId)) {
            return false;
        }

        try {
            String json = recordRepository.findRecords(userId);

            List<DocumentRecord> records =
                    json == null || json.isBlank()
                            ? new ArrayList<>()
                            : objectMapper.readValue(
                            json,
                            new TypeReference<List<DocumentRecord>>() {}
                    );

            DocumentRecord newRecord =
                    new DocumentRecord(documentTypeId, issueDate);

            if (records.contains(newRecord)) {
                return false;
            }

            records.add(newRecord);

            recordRepository.saveRecords(
                    userId,
                    objectMapper.writeValueAsString(records)
            );

            return true;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "서류 기록 저장에 실패했습니다.", e
            );
        }
    }

    public List<DocumentRecord> findRecords(Long userId) {

        if (userId == null || !hasRecordConsent(userId)) {
            return List.of();
        }

        try {
            String json = recordRepository.findRecords(userId);

            if (json == null || json.isBlank()) {
                return List.of();
            }

            return objectMapper.readValue(
                    json,
                    new TypeReference<List<DocumentRecord>>() {}
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "서류 기록 조회에 실패했습니다.", e
            );
        }
    }

    public List<DocumentRecord> findRecordsByType(
            Long userId,
            Long documentTypeId
    ) {
        if (documentTypeId == null) {
            return List.of();
        }

        return findRecords(userId).stream()
                .filter(record ->
                        documentTypeId.equals(record.documentTypeId()))
                .toList();
    }

    public DocumentValidity calculateValidity(
            LocalDate issueDate,
            Integer validityDays
    ) {
        if (issueDate == null) {
            throw new IllegalArgumentException(
                    "발급일이 필요합니다."
            );
        }

        if (validityDays == null) {
            return new DocumentValidity(
                    issueDate,
                    null,
                    null
            );
        }

        if (validityDays < 0) {
            throw new IllegalArgumentException(
                    "유효기간은 음수일 수 없습니다."
            );
        }

        LocalDate expiresAt = issueDate.plusDays(validityDays);

        return new DocumentValidity(
                issueDate,
                expiresAt,
                !LocalDate.now().isAfter(expiresAt)
        );
    }

    public record DocumentValidity(
            LocalDate issueDate,
            LocalDate expiresAt,
            Boolean valid
    ) {
    }

    public record DocumentRecord(
            Long documentTypeId,
            LocalDate issueDate
    ) {
    }

    public boolean deleteRecordsOnConsentWithdrawal(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID가 필요합니다.");
        }

        if (hasRecordConsent(userId)) {
            return false;
        }

        recordRepository.deleteRecords(userId);
        return true;
    }
}