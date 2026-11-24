package com.bokdeongi.backend.benefit.assist;

import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import com.bokdeongi.backend.benefit.application.ApplicationQuery;
import com.bokdeongi.backend.benefit.application.DocumentRecordService;
import com.bokdeongi.backend.benefit.dto.response.ApplyGuideResponse;
import com.bokdeongi.backend.benefit.dto.response.DocumentCheckResponse;
import com.bokdeongi.backend.benefit.dto.response.DocumentPrepResponse;
import com.bokdeongi.backend.benefit.dto.response.MissingDocumentResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.ocr.DocumentCheckException;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import com.bokdeongi.backend.benefit.service.ApplicationDocumentService;
import com.bokdeongi.backend.benefit.service.ApplicationGuideService;
import com.bokdeongi.backend.benefit.service.DocumentCheckService;
import com.bokdeongi.backend.benefit.service.MissingDocumentService;
import com.bokdeongi.backend.profile.service.ProfileService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * AI 신청 도우미 ② 서류 준비 · ③ 서류 검토 · ④ 신청 안내 (ASST-03·04·05). 신청 건(ASST-01, A)의 doc_checks에 상태를 쌓는다.
 *
 * <p>doc_checks 모양은 A의 진행률 계산(ApplicationProgressCalculator)과 같다: {"documents": {"서류키": {prepared, reviewed, ...}}}.
 * 서류 파일은 저장하지 않고 OCR 결과(종류·발급일·필수 항목)만 남긴다. 서류 내용은 LLM에 보내지 않는다 (D-78).
 */
@Service
public class ApplicationAssistService {

    static final String DOC_PROCESS = "DOC_PROCESS";

    private static final Logger log = LoggerFactory.getLogger(ApplicationAssistService.class);

    private final ApplicationQuery query;
    private final BenefitRepository benefitRepository;
    private final ApplicationDocumentService documentService;
    private final DocumentCheckService checkService;
    private final MissingDocumentService missingService;
    private final ApplicationGuideService guideService;
    private final ProfileService profileService;
    private final UserConsentRepository consentRepository;
    private final DocumentRecordService recordService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApplicationAssistService(ApplicationQuery query, BenefitRepository benefitRepository,
                                    ApplicationDocumentService documentService, DocumentCheckService checkService,
                                    MissingDocumentService missingService, ApplicationGuideService guideService,
                                    ProfileService profileService, UserConsentRepository consentRepository,
                                    DocumentRecordService recordService) {
        this.query = query;
        this.benefitRepository = benefitRepository;
        this.documentService = documentService;
        this.checkService = checkService;
        this.missingService = missingService;
        this.guideService = guideService;
        this.profileService = profileService;
        this.consentRepository = consentRepository;
        this.recordService = recordService;
    }

    /** ② 필요 서류와 준비 상태 */
    public AssistDto.DocumentsResponse documents(long userId, long applicationId) {
        ApplicationQuery.ApplicationRow row = row(userId, applicationId);
        DocumentPrepResponse prep = documentService.documents(benefit(row));
        return new AssistDto.DocumentsResponse(prep, states(row.docChecks(), keys(prep).keySet()));
    }

    /** ② 준비 완료 체크. OCR로 검토할 수 없는 서류는 준비 체크가 곧 검토 완료다 */
    public AssistDto.DocumentsResponse prepare(long userId, long applicationId, String key, boolean prepared) {
        ApplicationQuery.ApplicationRow row = inProgress(row(userId, applicationId));
        DocumentPrepResponse prep = documentService.documents(benefit(row));
        Map<String, DocumentPrepResponse.Item> keys = keys(prep);
        if (!keys.containsKey(key)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 신청에 필요한 서류가 아니에요: " + key);
        }
        DocumentPrepResponse.Item item = keys.get(key);
        boolean ocr = item != null && item.ocrSupported();

        ObjectNode checks = parse(row.docChecks());
        ObjectNode doc = documentNode(checks, key);
        doc.put("prepared", prepared);
        if (!ocr) {
            doc.put("reviewed", prepared);
        } else if (!prepared) {
            doc.put("reviewed", false);
        }
        save(row, Math.max(step(row), 2), checks);
        return new AssistDto.DocumentsResponse(prep, states(checks.toString(), keys.keySet()));
    }

    /**
     * ③ 서류 올려 검토 (로컬 OCR만). 서류 처리 동의(DOC_PROCESS, D-76)가 있어야 한다.
     *
     * @param documentTypeId 올린 서류 종류. 필요 서류이거나 그 대체 서류(A 또는 B)여야 한다
     */
    public DocumentCheckResponse upload(long userId, long applicationId, Long documentTypeId, MultipartFile file) {
        if (!agreed(userId)) {
            throw DocumentCheckException.consentRequired();
        }
        if (documentTypeId == null) {
            throw DocumentCheckException.invalidInput("올릴 서류 종류를 골라 주세요.");
        }
        ApplicationQuery.ApplicationRow row = inProgress(row(userId, applicationId));
        DocumentPrepResponse prep = documentService.documents(benefit(row));
        String key = itemKey(prep, documentTypeId);
        if (key == null) {
            throw DocumentCheckException.invalidInput("이 신청에 필요한 서류가 아니에요.");
        }

        DocumentCheckResponse r = checkService.check(documentTypeId, file);
        if (DocumentCheckService.RETRY.equals(r.result())) {
            return r; // 흐림·다른 서류는 저장하지 않고 다시 올리게 한다
        }
        ObjectNode checks = parse(row.docChecks());
        ObjectNode doc = documentNode(checks, key);
        boolean ready = DocumentCheckService.READY.equals(r.result());
        doc.put("prepared", true);
        doc.put("reviewed", ready);
        doc.put("result", r.result());
        doc.put("reason", r.reason());
        if (r.issueDate() == null) {
            doc.putNull("issueDate");
        } else {
            doc.put("issueDate", r.issueDate());
        }
        doc.put("documentTypeId", documentTypeId);
        save(row, Math.max(step(row), 3), checks);

        if (ready && r.issueDate() != null) {
            try {
                recordService.saveRecord(userId, documentTypeId, LocalDate.parse(r.issueDate()));
            } catch (RuntimeException e) { // 기록 실패가 검토 결과를 막지 않게
                log.warn("서류 발급일 기록 실패 applicationId={}: {}", applicationId, e.toString());
            }
        }
        return r;
    }

    /** ③ 부족 서류 보완 안내 */
    public MissingDocumentResponse missing(long userId, long applicationId) {
        ApplicationQuery.ApplicationRow row = row(userId, applicationId);
        Benefit benefit = benefit(row);
        DocumentPrepResponse prep = documentService.documents(benefit);
        Map<Long, MissingDocumentService.DocStatus> statuses = new HashMap<>();
        Map<String, DocumentPrepResponse.Item> keys = keys(prep);
        states(row.docChecks(), keys.keySet()).forEach((key, s) -> {
            DocumentPrepResponse.Item item = keys.get(key);
            if (item == null) {
                return; // 양식·못 알아본 서류는 보완 안내 대상이 아니다
            }
            if (s.result() != null) {
                Long id = s.documentTypeId() != null ? s.documentTypeId() : item.documentTypeId();
                statuses.put(id, new MissingDocumentService.DocStatus(s.result(), s.reason()));
            } else if (s.prepared() && !item.ocrSupported()) {
                statuses.put(item.documentTypeId(),
                        new MissingDocumentService.DocStatus(DocumentCheckService.READY, null));
            }
        });
        return missingService.missing(benefit, statuses);
    }

    /** ④ 신청 방법 안내 (단계 카드·신청 링크·입력값 복사 카드) */
    public ApplyGuideResponse guide(long userId, long applicationId) {
        ApplicationQuery.ApplicationRow row = row(userId, applicationId);
        return guideService.guide(benefit(row), profileService.getProfile(userId));
    }

    private boolean agreed(long userId) {
        return consentRepository.findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(userId, DOC_PROCESS)
                .map(c -> Boolean.TRUE.equals(c.getAgreed()))
                .orElse(false);
    }

    private ApplicationQuery.ApplicationRow row(long userId, long applicationId) {
        return query.findById(applicationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신청 정보를 찾을 수 없습니다."));
    }

    private static ApplicationQuery.ApplicationRow inProgress(ApplicationQuery.ApplicationRow row) {
        if (!"IN_PROGRESS".equals(row.appStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 신청만 수정할 수 있습니다.");
        }
        return row;
    }

    private Benefit benefit(ApplicationQuery.ApplicationRow row) {
        return benefitRepository.findById(row.benefitId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 혜택입니다."));
    }

    private static int step(ApplicationQuery.ApplicationRow row) {
        return row.step() == null ? 1 : row.step();
    }

    private void save(ApplicationQuery.ApplicationRow row, int step, ObjectNode checks) {
        String answers = row.appAnswers() == null || row.appAnswers().isBlank() ? "{}" : row.appAnswers();
        if (!query.saveProgress(row.id(), row.userId(), Math.min(step, 4), answers, checks.toString())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "신청 상태가 변경되어 저장할 수 없습니다.");
        }
    }

    /** A의 validDocumentKeys와 같은 키: 서류 종류 id, "form:i", "unknown:i". 서류 키는 Item, 나머지는 null */
    static Map<String, DocumentPrepResponse.Item> keys(DocumentPrepResponse prep) {
        Map<String, DocumentPrepResponse.Item> keys = new LinkedHashMap<>();
        for (DocumentPrepResponse.Group g : prep.groups()) {
            for (DocumentPrepResponse.Item item : g.items()) {
                if (item.documentTypeId() != null) {
                    keys.put(item.documentTypeId().toString(), item);
                }
            }
        }
        for (int i = 0; i < prep.forms().size(); i++) {
            keys.put("form:" + i, null);
        }
        for (int i = 0; i < prep.unknown().size(); i++) {
            keys.put("unknown:" + i, null);
        }
        return keys;
    }

    /** 올린 서류가 어느 필요 서류 자리인지 (대체 서류면 그 원래 서류 자리) */
    static String itemKey(DocumentPrepResponse prep, Long documentTypeId) {
        for (DocumentPrepResponse.Group g : prep.groups()) {
            for (DocumentPrepResponse.Item item : g.items()) {
                if (documentTypeId.equals(item.documentTypeId())
                        || item.orDocs().stream().anyMatch(o -> documentTypeId.equals(o.documentTypeId()))) {
                    return item.documentTypeId() == null ? null : item.documentTypeId().toString();
                }
            }
        }
        return null;
    }

    private Map<String, AssistDto.DocState> states(String docChecks, Iterable<String> keys) {
        JsonNode docs = parse(docChecks).path("documents");
        Map<String, AssistDto.DocState> out = new LinkedHashMap<>();
        for (String key : keys) {
            JsonNode d = docs.path(key);
            out.put(key, new AssistDto.DocState(d.path("prepared").asBoolean(false),
                    d.path("reviewed").asBoolean(false), text(d, "result"), text(d, "reason"),
                    text(d, "issueDate"), d.hasNonNull("documentTypeId") ? d.get("documentTypeId").asLong() : null));
        }
        return out;
    }

    private static String text(JsonNode d, String field) {
        return d.hasNonNull(field) ? d.get(field).asText() : null;
    }

    private ObjectNode parse(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode n = objectMapper.readTree(json);
            return n instanceof ObjectNode o ? o : objectMapper.createObjectNode();
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private ObjectNode documentNode(ObjectNode checks, String key) {
        ObjectNode docs = checks.get("documents") instanceof ObjectNode o ? o : checks.putObject("documents");
        return docs.get(key) instanceof ObjectNode o ? o : docs.putObject(key);
    }
}
