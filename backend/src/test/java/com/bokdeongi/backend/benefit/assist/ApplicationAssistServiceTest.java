package com.bokdeongi.backend.benefit.assist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.bokdeongi.backend.auth.entity.UserConsent;
import com.bokdeongi.backend.auth.repository.UserConsentRepository;
import com.bokdeongi.backend.benefit.application.ApplicationQuery;
import com.bokdeongi.backend.benefit.application.DocumentRecordService;
import com.bokdeongi.backend.benefit.dto.response.DocumentCheckResponse;
import com.bokdeongi.backend.benefit.dto.response.DocumentPrepResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.ocr.DocumentCheckException;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import com.bokdeongi.backend.benefit.service.ApplicationDocumentService;
import com.bokdeongi.backend.benefit.service.ApplicationGuideService;
import com.bokdeongi.backend.benefit.service.DocumentCheckService;
import com.bokdeongi.backend.benefit.service.MissingDocumentService;
import com.bokdeongi.backend.profile.service.ProfileService;

/** 신청 건·서류·OCR은 흉내만 낸다. 서류는 가짜 */
class ApplicationAssistServiceTest {

    private static final long USER = 7L;
    private static final long APP = 3L;
    private static final long REGISTER = 1L;   // 주민등록등본 (OCR)
    private static final long FAMILY = 2L;     // 가족관계증명서 (OCR 아님)
    private static final long ALT = 9L;        // 등본 대신 낼 수 있는 대체 서류

    private ApplicationQuery query;
    private DocumentCheckService checkService;
    private UserConsentRepository consentRepository;
    private DocumentRecordService recordService;
    private MissingDocumentService missingService;
    private ApplicationAssistService service;

    @BeforeEach
    void setUp() {
        query = mock(ApplicationQuery.class);
        BenefitRepository benefits = mock(BenefitRepository.class);
        ApplicationDocumentService documents = mock(ApplicationDocumentService.class);
        checkService = mock(DocumentCheckService.class);
        missingService = mock(MissingDocumentService.class);
        consentRepository = mock(UserConsentRepository.class);
        recordService = mock(DocumentRecordService.class);
        service = new ApplicationAssistService(query, benefits, documents, checkService, missingService,
                mock(ApplicationGuideService.class), mock(ProfileService.class), consentRepository, recordService);

        Benefit benefit = mock(Benefit.class);
        when(benefits.findById(11L)).thenReturn(Optional.of(benefit));
        DocumentPrepResponse.Choice alt = new DocumentPrepResponse.Choice(ALT, "대체 서류", "정부24", true, null, null);
        DocumentPrepResponse.Item register = new DocumentPrepResponse.Item(REGISTER, "주민등록등본", "주민등록등본",
                "정부24", true, null, 30, null, true, List.of(alt));
        DocumentPrepResponse.Item family = new DocumentPrepResponse.Item(FAMILY, "가족관계증명서", "가족관계증명서",
                "대법원", true, null, null, null, false, List.of());
        when(documents.documents(benefit)).thenReturn(new DocumentPrepResponse(
                List.of(new DocumentPrepResponse.Group("정부24", null, List.of(register, family))),
                List.of("신청서"), List.of(), List.of(), 3));
        row("IN_PROGRESS", "{}");
        when(query.saveProgress(anyLong(), anyLong(), anyInt(), anyString(), anyString())).thenReturn(true);
    }

    private void row(String status, String docChecks) {
        when(query.findById(APP, USER)).thenReturn(Optional.of(new ApplicationQuery.ApplicationRow(
                APP, USER, 11L, status, 2, "{}", docChecks, null)));
    }

    private void consent(boolean agreed) {
        UserConsent c = mock(UserConsent.class);
        when(c.getAgreed()).thenReturn(agreed);
        when(consentRepository.findTopByUserIdAndConsentTypeOrderByActedAtDescIdDesc(USER, "DOC_PROCESS"))
                .thenReturn(Optional.of(c));
    }

    private String savedChecks() {
        ArgumentCaptor<String> checks = ArgumentCaptor.forClass(String.class);
        verify(query).saveProgress(eq(APP), eq(USER), anyInt(), anyString(), checks.capture());
        return checks.getValue();
    }

    @Test
    void 필요_서류_키는_서류id와_양식() {
        AssistDto.DocumentsResponse r = service.documents(USER, APP);
        assertEquals(List.of("1", "2", "form:0"), List.copyOf(r.states().keySet()));
        assertFalse(r.states().get("1").prepared());
    }

    @Test
    void OCR이_아닌_서류는_준비_체크가_곧_검토_완료() {
        AssistDto.DocumentsResponse r = service.prepare(USER, APP, "2", true);
        assertTrue(r.states().get("2").prepared());
        assertTrue(r.states().get("2").reviewed());
        assertTrue(savedChecks().contains("\"2\":{\"prepared\":true,\"reviewed\":true}"));
    }

    @Test
    void OCR_서류는_준비_체크만으로는_검토_완료가_아니다() {
        AssistDto.DocumentsResponse r = service.prepare(USER, APP, "1", true);
        assertTrue(r.states().get("1").prepared());
        assertFalse(r.states().get("1").reviewed());
    }

    @Test
    void 필요_없는_서류_키나_진행_중이_아닌_신청은_막는다() {
        assertThrows(ResponseStatusException.class, () -> service.prepare(USER, APP, "99", true));
        row("SUBMITTED", "{}");
        assertThrows(ResponseStatusException.class, () -> service.prepare(USER, APP, "1", true));
    }

    @Test
    void 서류_처리_동의가_없으면_올릴_수_없다() {
        consent(false);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});
        assertEquals("CONSENT_REQUIRED", assertThrows(DocumentCheckException.class,
                () -> service.upload(USER, APP, REGISTER, file)).getCode());
        verify(checkService, never()).check(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 검토_통과면_준비_검토_완료로_저장하고_발급일을_기록한다() {
        consent(true);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});
        when(checkService.check(REGISTER, file)).thenReturn(new DocumentCheckResponse(REGISTER, "READY", "PASS",
                "RESIDENT_REGISTER", "2026-11-20", "서류가 확인됐어요.", List.of("세대주"), List.of()));
        service.upload(USER, APP, REGISTER, file);
        String checks = savedChecks();
        assertTrue(checks.contains("\"prepared\":true") && checks.contains("\"reviewed\":true")
                && checks.contains("\"result\":\"READY\""));
        verify(recordService).saveRecord(USER, REGISTER, LocalDate.of(2026, 11, 20));
    }

    @Test
    void 대체_서류를_올리면_원래_서류_자리에_저장한다() {
        consent(true);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});
        when(checkService.check(ALT, file)).thenReturn(new DocumentCheckResponse(ALT, "MISSING", "MISSING", null,
                "2026-11-20", "필수 항목을 못 찾았어요.", List.of(), List.of("세대주")));
        service.upload(USER, APP, ALT, file);
        String checks = savedChecks();
        assertTrue(checks.contains("\"1\":{") && checks.contains("\"documentTypeId\":9")
                && checks.contains("\"reviewed\":false"));
        verify(recordService, never()).saveRecord(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 다시_올려야_하는_결과는_저장하지_않는다() {
        consent(true);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});
        when(checkService.check(REGISTER, file)).thenReturn(new DocumentCheckResponse(REGISTER, "RETRY", "UNREADABLE",
                null, null, "다시 찍어 주세요.", List.of(), List.of()));
        assertEquals("RETRY", service.upload(USER, APP, REGISTER, file).result());
        verify(query, never()).saveProgress(anyLong(), anyLong(), anyInt(), anyString(), anyString());
    }

    @Test
    void 부족_서류_안내는_검토_결과와_OCR_아닌_서류의_준비_체크를_넘긴다() {
        row("IN_PROGRESS", "{\"documents\":{\"1\":{\"prepared\":true,\"reviewed\":false,\"result\":\"INVALID\","
                + "\"reason\":\"기한 지남\"},\"2\":{\"prepared\":true,\"reviewed\":true}}}");
        ArgumentCaptor<java.util.Map<Long, MissingDocumentService.DocStatus>> st =
                ArgumentCaptor.forClass(java.util.Map.class);
        service.missing(USER, APP);
        verify(missingService).missing(org.mockito.ArgumentMatchers.any(), st.capture());
        assertEquals("INVALID", st.getValue().get(REGISTER).result());
        assertEquals("READY", st.getValue().get(FAMILY).result());
        assertNull(st.getValue().get(ALT));
    }
}
