package com.bokdeongi.backend.benefit.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** OCR 서버 없이 응답만 흉내 낸다. 서류 파일은 가짜 바이트 */
class OcrClientTest {

    private static final byte[] FAKE_PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    private MockRestServiceServer server;
    private OcrClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ocr.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OcrClient(builder.build());
    }

    @Test
    void 충족_응답을_읽는다() {
        server.expect(requestTo("http://ocr.test/check")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"result":"PASS","detectedType":"RESIDENT_REGISTER","issueDate":"2026-11-10",
                         "foundFields":["세대주","주소","세대원"],"missingFields":[],"reason":"서류가 확인됐어요."}""",
                        MediaType.APPLICATION_JSON));
        OcrCheckResult r = client.check(FAKE_PNG, "fake.png", "RESIDENT_REGISTER", 90);
        assertEquals("PASS", r.result());
        assertEquals("RESIDENT_REGISTER", r.detectedType());
        assertEquals("2026-11-10", r.issueDate());
        assertEquals(List.of("세대주", "주소", "세대원"), r.foundFields());
        assertEquals(List.of(), r.missingFields());
        server.verify();
    }

    @Test
    void 흐린_사진은_발급일_없이_온다() {
        server.expect(requestTo("http://ocr.test/check")).andRespond(withSuccess("""
                {"result":"UNREADABLE","detectedType":null,"issueDate":null,"foundFields":[],"missingFields":[],
                 "reason":"사진이 흐려서 글자를 읽기 어려워요. 다시 찍어 주세요."}""", MediaType.APPLICATION_JSON));
        OcrCheckResult r = client.check(FAKE_PNG, "fake.png", "ENROLLMENT", null);
        assertEquals("UNREADABLE", r.result());
        assertNull(r.detectedType());
        assertNull(r.issueDate());
    }

    @Test
    void 파일이_크면_FILE_TOO_LARGE() {
        server.expect(requestTo("http://ocr.test/check")).andRespond(withStatus(HttpStatusCode.valueOf(413))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":{\"code\":\"FILE_TOO_LARGE\",\"message\":\"10MB 이하 파일만 올릴 수 있어요.\"}}"));
        DocumentCheckException e = assertThrows(DocumentCheckException.class,
                () -> client.check(FAKE_PNG, "fake.png", "ENROLLMENT", null));
        assertEquals(413, e.getStatus());
        assertEquals("FILE_TOO_LARGE", e.getCode());
    }

    @Test
    void 형식이_다르면_UNSUPPORTED_FORMAT() {
        server.expect(requestTo("http://ocr.test/check")).andRespond(withStatus(HttpStatusCode.valueOf(415))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":{\"code\":\"UNSUPPORTED_FORMAT\",\"message\":\"JPG, PNG, PDF만 올릴 수 있어요.\"}}"));
        DocumentCheckException e = assertThrows(DocumentCheckException.class,
                () -> client.check(new byte[] {1, 2, 3}, "fake.hwp", "ENROLLMENT", null));
        assertEquals("UNSUPPORTED_FORMAT", e.getCode());
    }

    @Test
    void OCR_서버가_꺼져_있으면_AI_UNAVAILABLE() {
        server.expect(requestTo("http://ocr.test/check")).andRespond(withException(new IOException("connection refused")));
        DocumentCheckException e = assertThrows(DocumentCheckException.class,
                () -> client.check(FAKE_PNG, "fake.png", "ENROLLMENT", null));
        assertEquals(502, e.getStatus());
        assertEquals("AI_UNAVAILABLE", e.getCode());
    }

    @Test
    void OCR_서버_500도_AI_UNAVAILABLE() {
        server.expect(requestTo("http://ocr.test/check")).andRespond(withStatus(HttpStatusCode.valueOf(500))
                .body("Internal Server Error"));
        assertEquals("AI_UNAVAILABLE", assertThrows(DocumentCheckException.class,
                () -> client.check(FAKE_PNG, "fake.png", "ENROLLMENT", null)).getCode());
    }

    @Test
    void 응답이_JSON이_아니면_AI_UNAVAILABLE() {
        assertEquals("AI_UNAVAILABLE", assertThrows(DocumentCheckException.class, () -> OcrClient.parse("<html>")).getCode());
        assertEquals("AI_UNAVAILABLE", assertThrows(DocumentCheckException.class, () -> OcrClient.parse("{}")).getCode());
    }

    @Test
    void 알_수_없는_서류_코드는_INVALID_INPUT() {
        DocumentCheckException e = OcrClient.fromError(400,
                "{\"detail\":{\"code\":\"UNKNOWN_DOC_TYPE\",\"message\":\"OCR 대상이 아닌 서류: X\"}}");
        assertEquals(400, e.getStatus());
        assertEquals("INVALID_INPUT", e.getCode());
    }
}
