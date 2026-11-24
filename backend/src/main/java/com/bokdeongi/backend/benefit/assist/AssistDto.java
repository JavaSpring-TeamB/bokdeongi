package com.bokdeongi.backend.benefit.assist;

import com.bokdeongi.backend.benefit.dto.response.DocumentPrepResponse;
import java.util.Map;

/** AI 신청 도우미 ②·③ 요청·응답 (ASST-03·04, /api/applications/{id}/documents) */
public final class AssistDto {

    private AssistDto() {
    }

    /**
     * 서류 하나의 진행 상태 (user_benefits.doc_checks.documents[key]).
     *
     * @param prepared       ② 준비 완료 체크 (서류를 올려 검토하면 자동으로 true)
     * @param reviewed       ③ 검토 완료. OCR 서류는 검토 결과가 READY일 때, 그 밖의 서류는 준비 체크할 때 true
     * @param result         ③ 검토 결과 READY / MISSING / INVALID (올린 적 없으면 null)
     * @param reason         검토 결과 안내 문구
     * @param issueDate      인식한 발급일 (yyyy-MM-dd)
     * @param documentTypeId 실제로 올린 서류 종류 (대체 서류를 올렸으면 그 id)
     */
    public record DocState(boolean prepared, boolean reviewed, String result, String reason, String issueDate,
                           Long documentTypeId) {
    }

    /**
     * ② 필요 서류와 준비 상태.
     *
     * @param documents 발급처별 서류 묶음 (기존 서류 준비 응답 그대로)
     * @param states    서류 키 → 상태. 키는 서류 종류 id 문자열, 신청서 양식은 "form:0", 못 알아본 서류는 "unknown:0"
     */
    public record DocumentsResponse(DocumentPrepResponse documents, Map<String, DocState> states) {
    }

    /** ② 준비 완료 체크 (@param prepared true면 준비 완료, false면 취소) */
    public record PrepareRequest(Boolean prepared) {
    }
}
