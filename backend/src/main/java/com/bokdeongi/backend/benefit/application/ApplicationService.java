package com.bokdeongi.backend.benefit.application;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import com.bokdeongi.backend.benefit.service.ApplicationDocumentService;
import com.bokdeongi.backend.benefit.dto.response.DocumentPrepResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

@Service
public class ApplicationService {

    private final ApplicationQuery query;
    private final BenefitRepository benefitRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationDocumentService documentService;

    public ApplicationService(
            ApplicationQuery query,
            BenefitRepository benefitRepository,
            ApplicationDocumentService documentService
    ) {
        this.query = query;
        this.benefitRepository = benefitRepository;
        this.documentService = documentService;
        this.objectMapper = new ObjectMapper();
    }

    // 신청 시작 또는 기존 신청 이어하기
    @Transactional
    public ApplicationResponse start(long userId, long benefitId) {

        Benefit benefit = findBenefit(benefitId);

        var existing = query.findByBenefit(userId, benefitId);

        // 기존 신청 건은 초기화하지 않고 그대로 반환
        if (existing.isPresent()
                && existing.get().appStatus() != null) {
            return toResponse(existing.get(), benefit);
        }

        // 신규 신청만 신청 가능 여부 검사
        validateStartable(benefit);

        query.start(
                userId,
                benefitId,
                benefit.getApiModifiedAt()
        );

        ApplicationQuery.ApplicationRow row =
                query.findByBenefit(userId, benefitId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                        "신청 정보를 저장하지 못했습니다."
                                ));

        return toResponse(row, benefit);
    }

    // 본인 신청 건 조회 및 이어하기
    @Transactional(readOnly = true)
    public ApplicationResponse get(long userId, long applicationId) {

        ApplicationQuery.ApplicationRow row =
                query.findById(applicationId, userId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "신청 정보를 찾을 수 없습니다."
                                ));

        Benefit benefit = findBenefit(row.benefitId());

        return toResponse(row, benefit);
    }

    // 신청 진행 단계 및 답변 자동 저장
    @Transactional
    public ApplicationResponse saveProgress(
            long userId,
            long applicationId,
            ApplicationSaveRequest request
    ) {
        ApplicationQuery.ApplicationRow row =
                query.findById(applicationId, userId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "신청 정보를 찾을 수 없습니다."
                                ));

        if (!"IN_PROGRESS".equals(row.appStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "진행 중인 신청만 수정할 수 있습니다."
            );
        }

        ObjectNode answers = toObjectNode(row.appAnswers());
        ObjectNode checks = toObjectNode(row.docChecks());

        Benefit benefit = findBenefit(row.benefitId());
        validateDocumentKeys(benefit, request.docChecks());

        // 요청에 포함된 필드만 기존 JSON에 병합
        merge(answers, request.appAnswers());
        merge(checks, request.docChecks());

        boolean saved = query.saveProgress(
                applicationId,
                userId,
                request.step(),
                answers.toString(),
                checks.toString()
        );

        if (!saved) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "신청 상태가 변경되어 저장할 수 없습니다."
            );
        }

        ApplicationQuery.ApplicationRow updated =
                query.findById(applicationId, userId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "저장된 신청 정보를 찾을 수 없습니다."
                                ));

        return toResponse(updated, benefit);
    }

    private ObjectNode toObjectNode(String json) {
        JsonNode node = parseJson(json);

        if (!node.isObject()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "저장된 신청 데이터는 JSON 객체여야 합니다."
            );
        }

        return (ObjectNode) node;
    }


    private void merge(
            ObjectNode target,
            java.util.Map<String, Object> updates
    ) {
        if (updates == null) {
            return;
        }

        JsonNode patch = objectMapper.valueToTree(updates);
        mergeObjects(target, patch);
    }

    private void mergeObjects(ObjectNode target, JsonNode patch) {
        patch.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode newValue = entry.getValue();
            JsonNode oldValue = target.get(key);

            if (newValue.isObject() && oldValue != null
                    && oldValue.isObject()) {
                mergeObjects((ObjectNode) oldValue, newValue);
            } else {
                target.set(key, newValue);
            }
        });
    }

    private Benefit findBenefit(long benefitId) {
        return benefitRepository.findById(benefitId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "존재하지 않는 혜택입니다."
                        ));
    }

    private void validateStartable(Benefit benefit) {

        if (!Benefit.STATUS_ACTIVE.equals(benefit.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "종료된 혜택은 새로 신청할 수 없습니다."
            );
        }

        if ("NO_APPLICATION".equalsIgnoreCase(
                benefit.getDateStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "신청 없이 받는 혜택입니다."
            );
        }

        if (benefit.getApplyEnd() != null
                && benefit.getApplyEnd().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "신청 기간이 종료된 혜택입니다."
            );
        }
    }

    private ApplicationResponse toResponse(
            ApplicationQuery.ApplicationRow row,
            Benefit benefit
    ) {

        JsonNode answers = parseJson(row.appAnswers());
        JsonNode checks = parseJson(row.docChecks());

        LocalDateTime started = row.startedApiModifiedAt();
        LocalDateTime current = benefit.getApiModifiedAt();

        boolean announcementChanged =
                started != null
                        && current != null
                        && !Objects.equals(started, current);


        // 기존 서류 준비 서비스에서 전체 서류 수 조회
        int documentCount = documentService.documents(benefit).total();

        Set<String> validKeys = validDocumentKeys(benefit);

        // 저장된 상태를 기준으로 진행률 계산
        ApplicationProgressCalculator.Progress progress =
                ApplicationProgressCalculator.calculate(
                        row.appStatus(),
                        row.step(),
                        answers,
                        checks,
                        documentCount,
                        validKeys
                );

        return new ApplicationResponse(
                row.id(),
                row.benefitId(),
                row.appStatus(),
                row.step(),
                answers.toString(),
                checks.toString(),
                started,
                announcementChanged,
                progress.stage(),
                progress.completedItems(),
                progress.totalItems(),
                progress.progressPercent()
        );
    }

        private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "저장된 신청 데이터 형식이 올바르지 않습니다."
            );
        }
    }

    private void validateDocumentKeys(
            Benefit benefit,
            java.util.Map<String, Object> docChecks
    ) {
        if (docChecks == null || !docChecks.containsKey("documents")) {
            return;
        }

        Object rawDocuments = docChecks.get("documents");

        if (!(rawDocuments instanceof java.util.Map<?, ?> documents)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "서류 목록은 JSON 객체여야 합니다."
            );
        }

        Set<String> validKeys = validDocumentKeys(benefit);

        for (Object key : documents.keySet()) {
            if (!(key instanceof String documentKey)
                    || !validKeys.contains(documentKey)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "존재하지 않는 서류입니다: " + key
                );
            }
        }
    }

    private Set<String> validDocumentKeys(Benefit benefit) {
        DocumentPrepResponse prep = documentService.documents(benefit);
        Set<String> validKeys = new HashSet<>();

        for (DocumentPrepResponse.Group group : prep.groups()) {
            for (DocumentPrepResponse.Item item : group.items()) {
                if (item.documentTypeId() != null) {
                    validKeys.add(item.documentTypeId().toString());
                }
            }
        }

        for (int i = 0; i < prep.forms().size(); i++) {
            validKeys.add("form:" + i);
        }

        for (int i = 0; i < prep.unknown().size(); i++) {
            validKeys.add("unknown:" + i);
        }

        return validKeys;
    }
}