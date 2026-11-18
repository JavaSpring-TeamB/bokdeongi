package com.bokdeongi.backend.benefit.ocr;

import java.util.List;

/**
 * OCR 서버 /check 응답 (ocr-server/README.md).
 *
 * @param result        PASS / MISSING / INVALID / TYPE_MISMATCH / UNREADABLE
 * @param detectedType  읽어 낸 서류 종류 코드 (RESIDENT_REGISTER ...). 모르면 null
 * @param issueDate     발급일 yyyy-MM-dd. 못 찾으면 null
 * @param foundFields   찾은 필수 항목 이름
 * @param missingFields 못 찾은 필수 항목 이름
 * @param reason        화면에 보여 줄 이유 (마스킹 끝난 글, 개인정보 없음)
 */
public record OcrCheckResult(String result, String detectedType, String issueDate, List<String> foundFields,
                             List<String> missingFields, String reason) {
}
