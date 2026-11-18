package com.bokdeongi.backend.benefit.service;

import com.bokdeongi.backend.benefit.dto.response.DocumentCheckResponse;
import com.bokdeongi.backend.benefit.entity.DocumentType;
import com.bokdeongi.backend.benefit.ocr.DocumentCheckException;
import com.bokdeongi.backend.benefit.ocr.OcrCheckResult;
import com.bokdeongi.backend.benefit.ocr.OcrClient;
import com.bokdeongi.backend.benefit.repository.DocumentTypeRepository;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 신청 도우미 ② 서류 업로드·검토 (ASST-03, AS-06). LLM을 부르지 않는다.
 *
 * <p>사진·PDF를 로컬 OCR 서버로 보내 서류 종류·발급일·필수 항목을 확인한다.
 * 파일은 메모리에서만 다루고 저장하지 않는다. 응답에는 마스킹된 글만 들어간다 (AGENTS.md 3·4번).
 *
 * <p>신청 건(ASST-01)이 생기면 컨트롤러가 이 결과를 user_benefits.doc_checks에 저장한다 (RETRY는 저장하지 않음).
 * 서류 처리 동의(DOC_PROCESS) 확인도 컨트롤러 단계에서 한다.
 */
@Service
@Transactional(readOnly = true)
public class DocumentCheckService {

    static final long MAX_BYTES = 10L * 1024 * 1024;

    public static final String READY = "READY";
    public static final String MISSING = "MISSING";
    public static final String INVALID = "INVALID";
    /** 다시 찍거나 맞는 서류를 올려야 함 (흐림, 다른 서류). doc_checks에 저장하지 않는다 */
    public static final String RETRY = "RETRY";

    /** document_types 이름 → OCR 서버 서류 코드 (D-18, ocr_supported = true인 4종) */
    static final Map<String, String> OCR_CODE = Map.of(
            "주민등록등본", "RESIDENT_REGISTER",
            "건강보험료 납부확인서", "HEALTH_INSURANCE_PAYMENT",
            "재학증명서", "ENROLLMENT",
            "소득금액증명원", "INCOME_AMOUNT");
    private static final Map<String, String> CODE_NAME = Map.of(
            "RESIDENT_REGISTER", "주민등록등본",
            "HEALTH_INSURANCE_PAYMENT", "건강보험료 납부확인서",
            "ENROLLMENT", "재학증명서",
            "INCOME_AMOUNT", "소득금액증명원");

    private final DocumentTypeRepository documentTypeRepository;
    private final OcrClient ocrClient;

    public DocumentCheckService(DocumentTypeRepository documentTypeRepository, OcrClient ocrClient) {
        this.documentTypeRepository = documentTypeRepository;
        this.ocrClient = ocrClient;
    }

    public DocumentCheckResponse check(Long documentTypeId, MultipartFile file) {
        DocumentType type = documentTypeRepository.findById(documentTypeId)
                .orElseThrow(() -> DocumentCheckException.notFound("서류 종류를 찾을 수 없어요."));
        if (file == null || file.isEmpty()) {
            throw DocumentCheckException.invalidInput("서류 파일을 올려 주세요.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw DocumentCheckException.fileTooLarge();
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw DocumentCheckException.invalidInput("파일을 읽지 못했어요. 다시 올려 주세요.");
        }
        try {
            return check(type, data, file.getOriginalFilename());
        } finally {
            Arrays.fill(data, (byte) 0); // 원본은 남기지 않는다
        }
    }

    DocumentCheckResponse check(DocumentType type, byte[] data, String filename) {
        String code = OCR_CODE.get(type.getName());
        if (!Boolean.TRUE.equals(type.getOcrSupported()) || code == null) {
            throw DocumentCheckException.invalidInput("이 서류는 사진 확인을 지원하지 않아요. 준비했으면 직접 체크해 주세요.");
        }
        if (data.length > MAX_BYTES) {
            throw DocumentCheckException.fileTooLarge();
        }
        OcrCheckResult r = ocrClient.check(data, filename, code, type.getDefaultValidityDays());
        return new DocumentCheckResponse(type.getId(), resultOf(r.result()), r.result(),
                r.detectedType() == null ? null : CODE_NAME.getOrDefault(r.detectedType(), r.detectedType()),
                r.issueDate(), r.reason(), r.foundFields(), r.missingFields());
    }

    /** OCR 판정 → 서류 상태 (doc_checks.result) */
    static String resultOf(String ocrResult) {
        return switch (ocrResult == null ? "" : ocrResult) {
            case "PASS" -> READY;
            case "MISSING" -> MISSING;
            case "INVALID" -> INVALID;
            default -> RETRY; // TYPE_MISMATCH, UNREADABLE
        };
    }
}
