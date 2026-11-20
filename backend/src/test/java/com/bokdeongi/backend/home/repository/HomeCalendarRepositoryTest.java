package com.bokdeongi.backend.home.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HomeCalendarRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private HomeCalendarRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new HomeCalendarRepository(jdbcTemplate);
    }

    @Test
    void upcomingDeadlinesShouldFilterDayPrecision() {
        LocalDate startDate = LocalDate.of(2026, 11, 19);
        LocalDate endDate = LocalDate.of(2026, 11, 30);

        repository.findUpcomingDeadlines(startDate, endDate);

        var sqlCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);

        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                any(org.springframework.jdbc.core.RowMapper.class),
                org.mockito.ArgumentMatchers.eq(startDate),
                org.mockito.ArgumentMatchers.eq(endDate)
        );

        String sql = sqlCaptor.getValue();

        assertThat(sql).contains("b.date_precision = 'DAY'");
        assertThat(sql).contains("b.date_status = 'PARSED'");
        assertThat(sql).contains("LIMIT 10");
    }
}