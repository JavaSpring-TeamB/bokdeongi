package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.bokdeongi.backend.benefit.dto.response.DocumentCheckResponse;
import com.bokdeongi.backend.benefit.entity.DocumentType;
import com.bokdeongi.backend.benefit.ocr.DocumentCheckException;
import com.bokdeongi.backend.benefit.ocr.OcrCheckResult;
import com.bokdeongi.backend.benefit.ocr.OcrClient;
import com.bokdeongi.backend.benefit.repository.DocumentTypeRepository;

/** OCR 서버는 흉내만 낸다. 서류 파일은 가짜 바이트 */
class DocumentCheckServiceTest {

    private static final byte[] FAKE = {(byte) 0x89, 'P', 'N', 'G', 9, 9};

    private DocumentTypeRepository repository;
    private OcrClient ocrClient;
    private DocumentCheckService service;

    private final DocumentType 등본 = new DocumentType("주민등록등본", "[\"등본\"]", "읍·면·동 행정복지센터", true,
            "https://www.gov.kr", "정부24", 90, null, true);
    private final DocumentType 통장 = new DocumentType("통장사본", "[]", "본인 거래 은행", true, null, "은행 앱", null, null,
            false);

    @BeforeEach
    void setUp() {
        repository = mock(DocumentTypeRepository.class);
        ocrClient = mock(OcrClient.class);
        service = new DocumentCheckService(repository, ocrClient);
        when(repository.findById(3L)).thenReturn(Optional.of(등본));
        when(repository.findById(4L)).thenReturn(Optional.of(통장));
    }

    private static OcrCheckResult ocr(String result, String type, String issueDate, String reason) {
        return new OcrCheckResult(result, type, issueDate, List.of("세대주"), List.of(), reason);
    }

    @Test
    void 충족이면_READY와_서류_이름() {
        when(ocrClient.check(any(), eq("등본.png"), eq("RESIDENT_REGISTER"), eq(90)))
                .thenReturn(ocr("PASS", "RESIDENT_REGISTER", "2026-11-10", "서류가 확인됐어요."));
        DocumentCheckResponse r = service.check(3L, new MockMultipartFile("file", "등본.png", "image/png", FAKE));
        assertEquals("READY", r.result());
        assertEquals("PASS", r.ocrResult());
        assertEquals("주민등록등본", r.recognizedType());
        assertEquals("2026-11-10", r.issueDate());
    }

    @Test
    void 발급일이_지나면_INVALID() {
        when(ocrClient.check(any(), anyString(), eq("RESIDENT_REGISTER"), eq(90)))
                .thenReturn(ocr("INVALID", "RESIDENT_REGISTER", "2026-06-02", "발급일이 90일을 넘었어요."));
        assertEquals("INVALID", service.check(3L, new MockMultipartFile("file", "a.png", "image/png", FAKE)).result());
    }

    @Test
    void 흐리거나_다른_서류면_다시_올리기() {
        assertEquals("RETRY", DocumentCheckService.resultOf("UNREADABLE"));
        assertEquals("RETRY", DocumentCheckService.resultOf("TYPE_MISMATCH"));
        assertEquals("MISSING", DocumentCheckService.resultOf("MISSING"));
    }

    @Test
    void 사진_확인을_지원하지_않는_서류는_OCR을_부르지_않는다() {
        DocumentCheckException e = assertThrows(DocumentCheckException.class,
                () -> service.check(4L, new MockMultipartFile("file", "a.png", "image/png", FAKE)));
        assertEquals("INVALID_INPUT", e.getCode());
        verify(ocrClient, never()).check(any(), any(), any(), any());
    }

    @Test
    void 없는_서류_종류는_NOT_FOUND() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertEquals("NOT_FOUND", assertThrows(DocumentCheckException.class,
                () -> service.check(99L, new MockMultipartFile("file", "a.png", "image/png", FAKE))).getCode());
    }

    @Test
    void 빈_파일은_INVALID_INPUT() {
        assertEquals("INVALID_INPUT", assertThrows(DocumentCheckException.class,
                () -> service.check(3L, new MockMultipartFile("file", "a.png", "image/png", new byte[0]))).getCode());
    }

    @Test
    void 파일이_10MB를_넘으면_OCR을_부르지_않는다() {
        byte[] big = new byte[(int) DocumentCheckService.MAX_BYTES + 1];
        assertEquals("FILE_TOO_LARGE", assertThrows(DocumentCheckException.class,
                () -> service.check(3L, new MockMultipartFile("file", "a.png", "image/png", big))).getCode());
        verify(ocrClient, never()).check(any(), any(), any(), any());
    }

    @Test
    void 확인이_끝나면_올린_파일_바이트를_지운다() throws Exception {
        byte[] seen = new byte[FAKE.length];
        when(ocrClient.check(any(), anyString(), anyString(), any())).thenAnswer(inv -> {
            byte[] data = inv.getArgument(0);
            System.arraycopy(data, 0, seen, 0, data.length);
            return ocr("PASS", "RESIDENT_REGISTER", "2026-11-10", "");
        });
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", FAKE.clone());
        service.check(3L, file);
        assertArrayEquals(FAKE, seen); // OCR 서버에는 원래 내용이 갔고
        assertArrayEquals(new byte[FAKE.length], file.getBytes()); // 끝난 뒤 메모리에는 0만 남는다
    }
}
