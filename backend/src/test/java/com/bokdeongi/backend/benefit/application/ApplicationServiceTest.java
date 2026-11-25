package com.bokdeongi.backend.benefit.application;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import com.bokdeongi.backend.benefit.service.ApplicationDocumentService;
import com.bokdeongi.backend.benefit.dto.response.DocumentPrepResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationServiceTest {

    private ApplicationQuery query;
    private BenefitRepository benefitRepository;
    private ApplicationDocumentService documentService;
    private ApplicationService service;

    private static final long USER_ID = 1L;
    private static final long APPLICATION_ID = 260L;
    private static final long BENEFIT_ID = 1361L;

    private LocalDate today;

    @BeforeEach
    void setUp() {

        query = mock(ApplicationQuery.class);
        benefitRepository = mock(BenefitRepository.class);
        documentService = mock(ApplicationDocumentService.class);

        service = new ApplicationService(
                query,
                benefitRepository,
                documentService
        );

        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    /**
     * 신규 신청이 가능한 활성 혜택을 설정한다.
     */
    private Benefit mockStartableBenefit() {
        Benefit benefit = mock(Benefit.class);

        when(benefit.getStatus())
                .thenReturn(Benefit.STATUS_ACTIVE);

        when(benefit.getDateStatus())
                .thenReturn("PARSED");

        when(benefit.getDatePrecision())
                .thenReturn("DAY");

        when(benefit.getApplyEnd())
                .thenReturn(today.plusDays(7));

        when(benefitRepository.findById(BENEFIT_ID))
                .thenReturn(Optional.of(benefit));

        when(documentService.documents(benefit))
                .thenReturn(new DocumentPrepResponse(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        0
                ));

        return benefit;
    }

    /**
     * 기존 신청이 없으면 1단계로 신규 신청을 시작한다.
     */
    @Test
    void shouldStartNewApplication() {
        Benefit benefit = mockStartableBenefit();

        ApplicationQuery.ApplicationRow row =
                new ApplicationQuery.ApplicationRow(
                        APPLICATION_ID,
                        USER_ID,
                        BENEFIT_ID,
                        "IN_PROGRESS",
                        1,
                        null,
                        null,
                        null
                );

        when(query.findByBenefit(USER_ID, BENEFIT_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(row));

        ApplicationResponse response =
                service.start(USER_ID, BENEFIT_ID);

        assertEquals(APPLICATION_ID, response.id());
        assertEquals("IN_PROGRESS", response.appStatus());
        assertEquals(1, response.step());

        verify(query).start(
                USER_ID,
                BENEFIT_ID,
                benefit.getApiModifiedAt()
        );
    }

    /**
     * 기존 신청의 진행 단계와 답변을 초기화하지 않는다.
     */
    @Test
    void shouldResumeExistingApplication() {
        mockStartableBenefit();

        ApplicationQuery.ApplicationRow row =
                new ApplicationQuery.ApplicationRow(
                        APPLICATION_ID,
                        USER_ID,
                        BENEFIT_ID,
                        "IN_PROGRESS",
                        3,
                        "{\"answer\":\"Y\"}",
                        "{}",
                        null
                );

        when(query.findByBenefit(USER_ID, BENEFIT_ID))
                .thenReturn(Optional.of(row));

        ApplicationResponse response =
                service.start(USER_ID, BENEFIT_ID);

        assertEquals(APPLICATION_ID, response.id());
        assertEquals(3, response.step());
        assertEquals("{\"answer\":\"Y\"}", response.appAnswers());

        verify(query, never()).start(
                anyLong(), anyLong(), any()
        );
    }

    /**
     * 종료 상태인 혜택은 새로 신청할 수 없다.
     */
    @Test
    void shouldRejectEndedBenefit() {
        Benefit benefit = mockStartableBenefit();

        when(benefit.getStatus())
                .thenReturn(Benefit.STATUS_ENDED);

        when(query.findByBenefit(USER_ID, BENEFIT_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.start(USER_ID, BENEFIT_ID)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(query, never()).start(
                anyLong(), anyLong(), any()
        );
    }

    /**
     * 확정된 신청 마감일이 지났으면 신규 신청을 제한한다.
     */
    @Test
    void shouldRejectPastDeadline() {
        Benefit benefit = mockStartableBenefit();

        when(benefit.getApplyEnd())
                .thenReturn(today.minusDays(1));

        when(query.findByBenefit(USER_ID, BENEFIT_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.start(USER_ID, BENEFIT_ID)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(query, never()).start(
                anyLong(), anyLong(), any()
        );
    }

    /**
     * 상시 신청 혜택은 과거 날짜 값만으로 신청을 제한하지 않는다.
     */
    @Test
    void shouldAllowAlwaysApplicationDespitePastDate() {
        Benefit benefit = mockStartableBenefit();

        when(benefit.getDateStatus())
                .thenReturn("ALWAYS");

        when(benefit.getApplyEnd())
                .thenReturn(today.minusDays(7));

        ApplicationQuery.ApplicationRow row =
                new ApplicationQuery.ApplicationRow(
                        APPLICATION_ID,
                        USER_ID,
                        BENEFIT_ID,
                        "IN_PROGRESS",
                        1,
                        null,
                        null,
                        null
                );

        when(query.findByBenefit(USER_ID, BENEFIT_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(row));

        ApplicationResponse response =
                service.start(USER_ID, BENEFIT_ID);

        assertEquals("IN_PROGRESS", response.appStatus());

        verify(query).start(
                USER_ID,
                BENEFIT_ID,
                benefit.getApiModifiedAt()
        );
    }

    /**
     * 본인의 신청 기록이 존재하지 않으면 404를 반환한다.
     */
    @Test
    void shouldRejectMissingApplication() {
        when(query.findById(APPLICATION_ID, USER_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.get(USER_ID, APPLICATION_ID)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                benefitRepository,
                documentService
        );
    }

    // 신청현황 테스트용 데이터 설정
    private void mockApplication(
            String appStatus,
            LocalDate deadline,
            String dateStatus,
            String datePrecision
    ) {

        Benefit benefit = mock(Benefit.class);

        when(benefit.getTitle()).thenReturn("테스트 혜택");
        when(benefit.getApplyEnd()).thenReturn(deadline);
        when(benefit.getDateStatus()).thenReturn(dateStatus);
        when(benefit.getDatePrecision()).thenReturn(datePrecision);

        ApplicationQuery.ApplicationRow row =
                new ApplicationQuery.ApplicationRow(
                        APPLICATION_ID,
                        USER_ID,
                        BENEFIT_ID,
                        appStatus,
                        1,
                        null,
                        null,
                        null
                );

        when(query.findAllByUserId(USER_ID))
                .thenReturn(List.of(row));

        when(benefitRepository.findById(BENEFIT_ID))
                .thenReturn(Optional.of(benefit));

        when(documentService.documents(benefit))
                .thenReturn(new DocumentPrepResponse(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        0
                ));
    }

    // 정확한 미래 마감일
    @Test
    void shouldCalculateFutureDeadline() {

        mockApplication(
                "IN_PROGRESS",
                today.plusDays(7),
                "PARSED",
                "DAY"
        );

        var response = service.getList(USER_ID);
        var item = response.inProgress().get(0);

        assertEquals(1, response.summary().inProgressCount());
        assertEquals(today.plusDays(7).toString(), item.deadline());
        assertEquals("D-7", item.deadlineLabel());
        assertEquals(7, item.dDay());

        assertTrue(item.calendarAvailable());
        assertNull(item.calendarUnavailableReason());
    }

    // 오늘 마감
    @Test
    void shouldCalculateTodayDeadline() {

        mockApplication(
                "IN_PROGRESS",
                today,
                "PARSED",
                "DAY"
        );

        var response = service.getList(USER_ID);
        var item = response.inProgress().get(0);

        assertEquals("D-Day", item.deadlineLabel());
        assertEquals(0, item.dDay());
        assertTrue(response.expired().isEmpty());

        assertTrue(item.calendarAvailable());
        assertNull(item.calendarUnavailableReason());
    }

    // 마감일이 지난 진행 중 신청
    @Test
    void shouldClassifyExpiredApplication() {

        mockApplication(
                "IN_PROGRESS",
                today.minusDays(1),
                "PARSED",
                "DAY"
        );

        var response = service.getList(USER_ID);
        var item = response.expired().get(0);

        assertEquals(1, response.summary().expiredCount());
        assertTrue(response.inProgress().isEmpty());
        assertEquals("마감됨", item.deadlineLabel());
        assertEquals(-1, item.dDay());

        assertTrue(item.calendarAvailable());
        assertNull(item.calendarUnavailableReason());
    }

    // 월 단위 날짜는 정확한 마감일로 처리하지 않음
    @Test
    void shouldNotCalculateMonthlyDeadline() {

        mockApplication(
                "IN_PROGRESS",
                today.minusDays(1),
                "PARSED",
                "MONTH"
        );

        var response = service.getList(USER_ID);
        var item = response.inProgress().get(0);

        assertNull(item.deadline());
        assertNull(item.dDay());
        assertEquals("공고 확인 필요", item.deadlineLabel());
        assertTrue(response.expired().isEmpty());

        assertFalse(item.calendarAvailable());
        assertEquals(
                "신청 마감일이 일 단위로 확정되지 않았습니다.",
                item.calendarUnavailableReason()
        );
    }

    // 상시 신청
    @Test
    void shouldHandleAlwaysApplication() {

        mockApplication(
                "IN_PROGRESS",
                today.minusDays(1),
                "ALWAYS",
                "DAY"
        );

        var response = service.getList(USER_ID);
        var item = response.inProgress().get(0);

        assertEquals("상시", item.deadlineLabel());
        assertNull(item.deadline());
        assertNull(item.dDay());
        assertTrue(response.expired().isEmpty());

        assertFalse(item.calendarAvailable());
        assertEquals(
                "상시 신청 혜택은 마감일이 없습니다.",
                item.calendarUnavailableReason()
        );
    }

    // 신청 일정 변동
    @Test
    void shouldHandleVariesApplication() {

        mockApplication(
                "IN_PROGRESS",
                today.plusDays(7),
                "VARIES",
                "DAY"
        );

        var response = service.getList(USER_ID);
        var item = response.inProgress().get(0);

        assertEquals("공고 확인 필요", item.deadlineLabel());
        assertNull(item.deadline());
        assertNull(item.dDay());

        assertFalse(item.calendarAvailable());
        assertEquals(
                "접수기관별로 신청 일정이 다릅니다.",
                item.calendarUnavailableReason()
        );
    }

    // 신청 불필요
    @Test
    void shouldHandleNoApplicationRequired() {

        mockApplication(
                "IN_PROGRESS",
                null,
                "NO_APPLICATION",
                "NONE"
        );

        var response = service.getList(USER_ID);
        var item = response.inProgress().get(0);

        assertEquals("신청 불필요", item.deadlineLabel());
        assertNull(item.deadline());
        assertNull(item.dDay());

        assertFalse(item.calendarAvailable());
        assertEquals(
                "별도 신청이 필요 없는 혜택입니다.",
                item.calendarUnavailableReason()
        );
    }

    // 완료된 신청은 마감일이 지나도 완료 목록 유지
    @Test
    void shouldKeepCompletedApplicationAfterDeadline() {

        mockApplication(
                "SELECTED",
                today.minusDays(7),
                "PARSED",
                "DAY"
        );

        var response = service.getList(USER_ID);

        assertEquals(1, response.summary().completedCount());
        assertEquals(0, response.summary().expiredCount());
        assertEquals("SELECTED",
                response.completed().get(0).appStatus());

        assertTrue(response.expired().isEmpty());

        var item = response.completed().get(0);

        assertTrue(item.calendarAvailable());
        assertNull(item.calendarUnavailableReason());
    }

    @Test
    void shouldRejectCalendarWithoutDeadline() {

        mockApplication(
                "IN_PROGRESS",
                null,
                "UNKNOWN",
                "NONE"
        );

        var response = service.getList(USER_ID);
        var item = response.inProgress().get(0);

        assertNull(item.deadline());
        assertNull(item.dDay());

        assertFalse(item.calendarAvailable());
        assertEquals(
                "정확한 신청 마감일을 확인할 수 없습니다.",
                item.calendarUnavailableReason()
        );
    }
}