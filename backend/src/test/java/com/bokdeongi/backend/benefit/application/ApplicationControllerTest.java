package com.bokdeongi.backend.benefit.application;

import com.bokdeongi.backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentRecordControllerTest {

    private MockMvc mockMvc;
    private DocumentRecordService service;
    private Authentication authentication;

    private static final String URL =
            "/api/me/document-records";

    @BeforeEach
    void setUp() {
        service = mock(DocumentRecordService.class);

        authentication =
                new UsernamePasswordAuthenticationToken(
                        "1",
                        null,
                        List.of()
                );

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new DocumentRecordController(service)
                )
                .setControllerAdvice(
                        new GlobalExceptionHandler()
                )
                .build();
    }

    // 전체 서류 기록 조회
    @Test
    void shouldGetAllDocumentRecords() throws Exception {
        when(service.getDocumentRecords(1L, null))
                .thenReturn(
                        new DocumentRecordResponse(List.of())
                );

        mockMvc.perform(
                        get(URL)
                                .principal(authentication)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records").isArray())
                .andExpect(jsonPath("$.records").isEmpty());

        verify(service).getDocumentRecords(1L, null);
    }

    // 특정 서류 종류 조회
    @Test
    void shouldGetRecordsByDocumentType() throws Exception {
        when(service.getDocumentRecords(1L, 256L))
                .thenReturn(
                        new DocumentRecordResponse(List.of())
                );

        mockMvc.perform(
                        get(URL)
                                .param("documentTypeId", "256")
                                .principal(authentication)
                )
                .andExpect(status().isOk());

        verify(service).getDocumentRecords(1L, 256L);
    }

    // 정상 서류 기록 등록
    @Test
    void shouldRegisterDocumentRecord() throws Exception {
        LocalDate issueDate =
                LocalDate.now(ZoneId.of("Asia/Seoul"))
                        .minusDays(1);

        mockMvc.perform(
                        post(URL)
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "documentTypeId": 256,
                                            "issueDate": "%s"
                                        }
                                        """.formatted(issueDate))
                )
                .andExpect(status().isNoContent());

        verify(service).registerDocumentRecord(
                1L,
                new DocumentRecordRequest(256L, issueDate)
        );
    }

    // 필수 입력값 누락
    @Test
    void shouldRejectMissingRequiredFields() throws Exception {
        mockMvc.perform(
                        post(URL)
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    // 잘못된 서류 종류 ID
    @Test
    void shouldRejectInvalidDocumentTypeId() throws Exception {
        mockMvc.perform(
                        post(URL)
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "documentTypeId": -1,
                                            "issueDate": "2026-01-01"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    // 미래 발급일 등록 방지
    @Test
    void shouldRejectFutureIssueDate() throws Exception {
        LocalDate futureDate =
                LocalDate.now(ZoneId.of("Asia/Seoul"))
                        .plusDays(2);

        mockMvc.perform(
                        post(URL)
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "documentTypeId": 256,
                                            "issueDate": "%s"
                                        }
                                        """.formatted(futureDate))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    // DOC_RECORD 미동의 사용자 조회 제한
    @Test
    void shouldRejectLookupWithoutConsent() throws Exception {
        when(service.getDocumentRecords(1L, null))
                .thenThrow(
                        DocumentRecordException.consentRequired()
                );

        mockMvc.perform(
                        get(URL)
                                .principal(authentication)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CONSENT_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("서류 유효기간 기억에 동의해야 합니다."));
    }

    // 인증 정보 없는 사용자 접근 제한
    @Test
    void shouldRejectUnauthenticatedUser() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectRegistrationWithoutConsent() throws Exception {
        doThrow(DocumentRecordException.consentRequired())
                .when(service)
                .registerDocumentRecord(eq(1L), any());

        mockMvc.perform(
                        post(URL)
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "documentTypeId": 1,
                                        "issueDate": "2026-01-01"
                                    }
                                    """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CONSENT_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("서류 유효기간 기억에 동의해야 합니다."));
    }

    @Test
    void shouldReturnNotFoundForUnknownDocumentType()
            throws Exception {

        doThrow(DocumentRecordException.documentTypeNotFound())
                .when(service)
                .registerDocumentRecord(eq(1L), any());

        mockMvc.perform(
                        post(URL)
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "documentTypeId": 999999999,
                                        "issueDate": "2026-01-01"
                                    }
                                    """)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("DOCUMENT_TYPE_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("존재하지 않는 서류 종류입니다."));
    }
}