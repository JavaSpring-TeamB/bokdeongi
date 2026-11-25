package com.bokdeongi.backend.benefit.favorite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 혜택 관심 등록, 해제 및 조회 서비스 테스트.
 */
class FavoriteToggleServiceTest {

    private JdbcTemplate jdbc;
    private FavoriteToggleService service;

    private static final long USER_ID = 137L;
    private static final long BENEFIT_ID = 3L;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new FavoriteToggleService(jdbc);
    }

    /**
     * 존재하는 혜택으로 설정한다.
     */
    private void mockExistingBenefit() {
        when(jdbc.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(BENEFIT_ID)
        )).thenReturn(1);
    }

    /**
     * 관심 등록 시 true를 반환한다.
     */
    @Test
    void shouldAddFavorite() {
        mockExistingBenefit();

        when(jdbc.update(
                anyString(),
                eq(USER_ID),
                eq(BENEFIT_ID)
        )).thenReturn(1);

        assertTrue(service.add(USER_ID, BENEFIT_ID));

        verify(jdbc).update(
                contains("ON DUPLICATE KEY UPDATE"),
                eq(USER_ID),
                eq(BENEFIT_ID)
        );
    }

    /**
     * 이미 등록된 혜택을 반복 등록해도 true를 반환한다.
     */
    @Test
    void shouldAllowRepeatedAdd() {
        mockExistingBenefit();

        when(jdbc.update(
                anyString(),
                eq(USER_ID),
                eq(BENEFIT_ID)
        )).thenReturn(0);

        assertTrue(service.add(USER_ID, BENEFIT_ID));
    }

    /**
     * 관심 해제 시 false를 반환한다.
     */
    @Test
    void shouldRemoveFavorite() {
        mockExistingBenefit();

        when(jdbc.update(
                anyString(),
                eq(USER_ID),
                eq(BENEFIT_ID)
        )).thenReturn(1);

        assertFalse(service.remove(USER_ID, BENEFIT_ID));

        verify(jdbc).update(
                contains("bookmarked = FALSE"),
                eq(USER_ID),
                eq(BENEFIT_ID)
        );
    }

    /**
     * 이미 해제된 혜택을 다시 해제해도 false를 반환한다.
     */
    @Test
    void shouldAllowRepeatedRemove() {
        mockExistingBenefit();

        when(jdbc.update(
                anyString(),
                eq(USER_ID),
                eq(BENEFIT_ID)
        )).thenReturn(0);

        assertFalse(service.remove(USER_ID, BENEFIT_ID));
    }

    /**
     * 관심 등록된 혜택의 조회 결과는 true이다.
     */
    @Test
    void shouldReturnTrueWhenBookmarked() {
        mockExistingBenefit();

        when(jdbc.query(
                anyString(),
                org.mockito.ArgumentMatchers
                        .<ResultSetExtractor<Boolean>>any(),
                eq(USER_ID),
                eq(BENEFIT_ID)
        )).thenReturn(true);

        assertTrue(service.isBookmarked(USER_ID, BENEFIT_ID));
    }

    /**
     * 관심 등록되지 않은 혜택의 조회 결과는 false이다.
     */
    @Test
    void shouldReturnFalseWhenNotBookmarked() {
        mockExistingBenefit();

        when(jdbc.query(
                anyString(),
                org.mockito.ArgumentMatchers
                        .<ResultSetExtractor<Boolean>>any(),
                eq(USER_ID),
                eq(BENEFIT_ID)
        )).thenReturn(false);

        assertFalse(service.isBookmarked(USER_ID, BENEFIT_ID));
    }

    /**
     * 존재하지 않는 혜택은 관심 등록할 수 없다.
     */
    @Test
    void shouldRejectAddWhenBenefitDoesNotExist() {
        when(jdbc.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(BENEFIT_ID)
        )).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.add(USER_ID, BENEFIT_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND,
                exception.getStatusCode());

        verify(jdbc, never()).update(
                anyString(),
                eq(USER_ID),
                eq(BENEFIT_ID)
        );
    }

    /**
     * 존재하지 않는 혜택은 관심 해제할 수 없다.
     */
    @Test
    void shouldRejectRemoveWhenBenefitDoesNotExist() {
        when(jdbc.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(BENEFIT_ID)
        )).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.remove(USER_ID, BENEFIT_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND,
                exception.getStatusCode());

        verify(jdbc, never()).update(
                anyString(),
                eq(USER_ID),
                eq(BENEFIT_ID)
        );
    }

    /**
     * 존재하지 않는 혜택의 관심 여부를 조회하면 404를 반환한다.
     */
    @Test
    void shouldRejectLookupWhenBenefitDoesNotExist() {
        when(jdbc.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(BENEFIT_ID)
        )).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.isBookmarked(USER_ID, BENEFIT_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND,
                exception.getStatusCode());
    }
}