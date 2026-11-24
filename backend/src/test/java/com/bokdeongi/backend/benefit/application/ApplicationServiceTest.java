package com.bokdeongi.backend.benefit.application;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import com.bokdeongi.backend.benefit.service.ApplicationDocumentService;
import com.bokdeongi.backend.benefit.dto.response.DocumentPrepResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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