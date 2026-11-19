package com.bokdeongi.backend.benefit.dto.response;

import java.util.List;

/**
 * ② 서류 업로드·검토 결과 (ASST-03, POST /api/applications/{id}/documents).
 *
 * @param documentTypeId 올린 서류 종류
 * @param result         READY(충족) / MISSING(필수 항목 없음) / INVALID(발급일 지남·없음) / RETRY(흐림·다른 서류 → 다시 올리기)
 * @param ocrResult      OCR 서버 원래 판정 PASS / MISSING / INVALID / TYPE_MISMATCH / UNREADABLE (화면 문구 고를 때)
 * @param recognizedType 읽어 낸 서류 이름 ("주민등록등본"). 모르면 null
 * @param issueDate      읽어 낸 발급일 yyyy-MM-dd. 못 찾으면 null
 * @param reason         화면에 보여 줄 이유
 * @param foundFields    찾은 필수 항목
 * @param missingFields  못 찾은 필수 항목
 */
public record DocumentCheckResponse(Long documentTypeId, String result, String ocrResult, String recognizedType,
                                    String issueDate, String reason, List<String> foundFields,
                                    List<String> missingFields) {
}
