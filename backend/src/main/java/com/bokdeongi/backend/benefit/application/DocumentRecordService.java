package com.bokdeongi.backend.benefit.application;

import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import com.bokdeongi.backend.benefit.entity.DocumentType;
import com.bokdeongi.backend.benefit.repository.DocumentTypeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentRecordService {

    private static final String DOC_RECORD = "DOC_RECORD";

    private final UserConsentRepository consentRepository;
    private final DocumentRecordRepository recordRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    public DocumentRecordService(
            UserConsentRepository consentRepository,
            DocumentRecordRepository recordRepository,
            DocumentTypeRepository documentTypeRepository
    ) {
        this.consentRepository = consentRepository;
        this.recordRepository = recordRepository;
        this.documentTypeRepository = documentTypeRepository;
    }

    public boolean hasRecordConsent(Long userId) {
        return consentRepository
                .findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(
                        userId, DOC_RECORD
                )
                .map(consent -> Boolean.TRUE.equals(consent.getAgreed()))
                .orElse(false);
    }

    @Transactional
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

        // 사용자 행 잠금
        if (!recordRepository.lockUser(userId)) {
            return false;
        }

        // 잠금 획득 후 동의 여부 확인
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

    /**
     * 기억한 서류 기록과 유효기간 정보를 조회한다.
     * documentTypeId가 null이면 전체 기록을 조회한다.
     */
    public DocumentRecordResponse getDocumentRecords(
            Long userId,
            Long documentTypeId
    ) {
        requireRecordConsent(userId);

        List<DocumentRecord> records = documentTypeId == null
                ? findRecords(userId)
                : findRecordsByType(userId, documentTypeId);

        List<DocumentRecordResponse.Item> items = new ArrayList<>();

        for (DocumentRecord record : records) {

            DocumentType documentType = documentTypeRepository
                    .findById(record.documentTypeId())
                    .orElse(null);

            if (documentType == null) {
                continue;
            }

            Integer validityDays =
                    documentType.getDefaultValidityDays();

            DocumentValidity validity = calculateValidity(
                    record.issueDate(),
                    validityDays
            );

            items.add(new DocumentRecordResponse.Item(
                    record.documentTypeId(),
                    documentType.getName(),
                    record.issueDate(),
                    validityDays,
                    validity.expiresAt(),
                    validity.valid()
            ));
        }

        return new DocumentRecordResponse(items);
    }

    /**
     * HTTP API를 통해 서류 발급일을 직접 등록한다.
     */
    @Transactional
    public void registerDocumentRecord(
            Long userId,
            DocumentRecordRequest request
    ) {
        if (!recordRepository.lockUser(userId)) {
            throw DocumentRecordException.userNotFound();
        }

        requireRecordConsent(userId);

        if (request.documentTypeId() == null
                || request.documentTypeId() <= 0) {
            throw new IllegalArgumentException(
                    "올바른 서류 종류 ID가 필요합니다."
            );
        }

        if (request.issueDate() == null) {
            throw new IllegalArgumentException(
                    "서류 발급일이 필요합니다."
            );
        }

        if (request.issueDate().isAfter(
                LocalDate.now(ZoneId.of("Asia/Seoul")))) {
            throw new IllegalArgumentException(
                    "서류 발급일은 미래일 수 없습니다."
            );
        }

        if (!documentTypeRepository.existsById(
                request.documentTypeId())) {
            throw DocumentRecordException.documentTypeNotFound();
        }

        saveRecord(
                userId,
                request.documentTypeId(),
                request.issueDate()
        );
    }

    /**
     * 서류 유효기간 기억 동의 여부를 검증한다.
     */
    private void requireRecordConsent(Long userId) {
        if (!hasRecordConsent(userId)) {
            throw DocumentRecordException.consentRequired();
        }
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
                !LocalDate.now(ZoneId.of("Asia/Seoul")).isAfter(expiresAt)
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