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
import java.util.ArrayList;
import java.util.List;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;

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

    // 로그인한 사용자의 신청 현황 전체 조회
    @Transactional(readOnly = true)
    public ApplicationListResponse getList(long userId) {

        List<ApplicationQuery.ApplicationRow> rows =
                query.findAllByUserId(userId);

        List<ApplicationListResponse.Item> inProgress =
                new ArrayList<>();
        List<ApplicationListResponse.Item> completed =
                new ArrayList<>();
        List<ApplicationListResponse.Item> expired =
                new ArrayList<>();

        LocalDate today = LocalDate.now( ZoneId.of("Asia/Seoul") );

        for (ApplicationQuery.ApplicationRow row : rows) {

            Benefit benefit = findBenefit(row.benefitId());

            // 기존 신청 상세 응답의 진행률 계산 재사용
            ApplicationResponse response =
                    toResponse(row, benefit);

            String status = row.appStatus();
            String dateStatus = benefit.getDateStatus();
            LocalDate deadline = benefit.getApplyEnd();

            String deadlineLabel;
            Integer dDay = null;

            // 날짜 상태를 우선 확인한다.
            if ("ALWAYS".equalsIgnoreCase(dateStatus)) {

                deadline = null;
                deadlineLabel = "상시";

            } else if ("VARIES".equalsIgnoreCase(dateStatus)) {

                deadline = null;
                deadlineLabel = "공고 확인 필요";

            } else if ("NO_APPLICATION".equalsIgnoreCase(dateStatus)) {

                deadline = null;
                deadlineLabel = "신청 불필요";

            } else if (deadline != null
                    && "DAY".equalsIgnoreCase(
                    benefit.getDatePrecision())) {

                // 일 단위로 확정된 마감일만 D-Day 계산
                long remaining =
                        ChronoUnit.DAYS.between(today, deadline);

                if (remaining >= Integer.MIN_VALUE
                        && remaining <= Integer.MAX_VALUE) {
                    dDay = (int) remaining;
                }

                if (remaining < 0) {
                    deadlineLabel = "마감됨";
                } else if (remaining == 0) {
                    deadlineLabel = "D-Day";
                } else {
                    deadlineLabel = "D-" + remaining;
                }

            } else {

                // 월 단위 또는 불명확한 날짜는 정확한 마감일로 제공하지 않는다.
                deadline = null;
                deadlineLabel = "공고 확인 필요";
            }

            ApplicationListResponse.Item item =
                    new ApplicationListResponse.Item(
                            row.id(),
                            row.benefitId(),
                            benefit.getTitle(),
                            status,
                            response.stage(),
                            row.step(),
                            response.completedItems(),
                            response.totalItems(),
                            response.progressPercent(),
                            deadline == null
                                    ? null
                                    : deadline.toString(),
                            deadlineLabel,
                            dDay
                    );

            if ("IN_PROGRESS".equals(status)) {
                if (dDay != null && dDay < 0) {
                    expired.add(item);
                } else {
                    inProgress.add(item);
                }
            } else {
                completed.add(item);
            }
        }

        ApplicationListResponse.Summary summary =
                new ApplicationListResponse.Summary(
                        rows.size(),
                        inProgress.size(),
                        completed.size(),
                        expired.size()
                );

        return new ApplicationListResponse(
                summary,
                inProgress,
                completed,
                expired
        );
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

    // 사용자가 직접 신청 완료 및 선정·미선정 결과 기록
    @Transactional
    public ApplicationResponse updateStatus(
            long userId,
            long applicationId,
            ApplicationStatusUpdateRequest request
    ) {
        // 본인 신청 건 조회
        ApplicationQuery.ApplicationRow row =
                query.findById(applicationId, userId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "신청 정보를 찾을 수 없습니다."
                                ));

        if (request == null || request.status() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "변경할 신청 상태를 입력해 주세요."
            );
        }

        String currentStatus = row.appStatus();
        String newStatus = request.status().name();

        // 허용된 상태 변경인지 확인
        boolean allowed = switch (currentStatus) {
            case "IN_PROGRESS" ->
                    "SUBMITTED".equals(newStatus)
                            || "CANCELED".equals(newStatus);

            case "SUBMITTED" ->
                    "SELECTED".equals(newStatus)
                            || "REJECTED".equals(newStatus);

            default -> false;
        };

        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "현재 신청 상태에서는 요청한 상태로 변경할 수 없습니다."
            );
        }

        // 기존 상태가 일치하는 경우에만 변경
        boolean updated = query.updateStatus(
                applicationId,
                userId,
                currentStatus,
                newStatus
        );

        if (!updated) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "신청 상태가 변경되어 다시 확인해야 합니다."
            );
        }

        // 변경된 신청 정보 반환
        ApplicationQuery.ApplicationRow updatedRow =
                query.findById(applicationId, userId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "변경된 신청 정보를 찾을 수 없습니다."
                                ));

        Benefit benefit = findBenefit(updatedRow.benefitId());

        return toResponse(updatedRow, benefit);
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


        // 동일한 서류 목록을 기준으로 진행률 계산
        DocumentPrepResponse prep = documentService.documents(benefit);

        int documentCount = prep.total();
        Set<String> validKeys = validDocumentKeys(prep);

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
        return validDocumentKeys(documentService.documents(benefit));
    }

    private Set<String> validDocumentKeys(DocumentPrepResponse prep) {
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