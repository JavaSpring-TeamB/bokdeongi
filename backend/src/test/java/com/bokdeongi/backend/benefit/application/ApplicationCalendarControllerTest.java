package com.bokdeongi.backend.benefit.application;

import com.bokdeongi.backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationCalendarControllerTest {

    private MockMvc mockMvc;
    private ApplicationService service;
    private ApplicationCalendarService calendarService;
    private Authentication authentication;

    private static final long USER_ID = 1L;
    private static final long APPLICATION_ID = 260L;

    private static final String URL =
            "/api/applications/260/calendar.ics";

    @BeforeEach
    void setUp() {
        service = mock(ApplicationService.class);
        calendarService = mock(ApplicationCalendarService.class);

        authentication =
                new UsernamePasswordAuthenticationToken(
                        "1",
                        null,
                        List.of()
                );

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ApplicationController(
                                service,
                                calendarService
                        )
                )
                .setControllerAdvice(
                        new GlobalExceptionHandler()
                )
                .build();
    }

    // 정상 캘린더 다운로드
    @Test
    void shouldDownloadCalendar() throws Exception {
        String ics =
                "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n";

        when(calendarService.createCalendar(
                USER_ID, APPLICATION_ID
        )).thenReturn(ics);

        mockMvc.perform(
                        get(URL)
                                .principal(authentication)
                                .accept("text/calendar")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "text/calendar;charset=UTF-8"
                ))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"application-260.ics\""
                ))
                .andExpect(content().string(ics));

        verify(calendarService).createCalendar(
                USER_ID, APPLICATION_ID
        );
    }

    // 캘린더 등록 불가
    @Test
    void shouldRejectUnavailableCalendar() throws Exception {
        when(calendarService.createCalendar(
                USER_ID, APPLICATION_ID
        )).thenThrow(
                ApplicationCalendarException.unavailable(
                        "상시 신청 혜택은 마감일이 없습니다."
                )
        );

        mockMvc.perform(
                        get(URL)
                                .principal(authentication)
                                .accept("text/calendar")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value("CALENDAR_UNAVAILABLE"))
                .andExpect(jsonPath("$.message")
                        .value("상시 신청 혜택은 마감일이 없습니다."));
    }

    // 존재하지 않거나 접근할 수 없는 신청 기록
    @Test
    void shouldRejectMissingApplication() throws Exception {
        when(calendarService.createCalendar(
                USER_ID, APPLICATION_ID
        )).thenThrow(
                ApplicationCalendarException.applicationNotFound()
        );

        mockMvc.perform(
                        get(URL)
                                .principal(authentication)
                                .accept("text/calendar")
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value("APPLICATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("신청 또는 관심 혜택 정보를 찾을 수 없습니다."));
    }

    // 연결된 혜택 정보 없음
    @Test
    void shouldRejectMissingBenefit() throws Exception {
        when(calendarService.createCalendar(
                USER_ID, APPLICATION_ID
        )).thenThrow(
                ApplicationCalendarException.benefitNotFound()
        );

        mockMvc.perform(
                        get(URL)
                                .principal(authentication)
                                .accept("text/calendar")
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value("BENEFIT_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("혜택 정보를 찾을 수 없습니다."));
    }

    // 인증 정보 없는 사용자 접근 제한
    @Test
    void shouldRejectUnauthenticatedUser() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(calendarService);
    }
}