package com.bokdeongi.backend.home.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HomeQueryRepositoryTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private HomeQueryRepository repository;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();

        jdbcTemplate = new JdbcTemplate(database);
        repository = new HomeQueryRepository(jdbcTemplate);

        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY,
                    profile_version INT NOT NULL
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE benefits (
                    id BIGINT PRIMARY KEY,
                    status VARCHAR(30) NOT NULL
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE eligibility_results (
                    user_id BIGINT NOT NULL,
                    benefit_id BIGINT NOT NULL,
                    profile_version INT NOT NULL,
                    result VARCHAR(30) NOT NULL,
                    near_miss BOOLEAN DEFAULT FALSE
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE sync_logs (
                    id BIGINT PRIMARY KEY,
                    operation VARCHAR(30),
                    status VARCHAR(30),
                    finished_at TIMESTAMP
                )
                """);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void 현재_프로필의_활성_혜택만_집계한다() {
        jdbcTemplate.update("""
                INSERT INTO users VALUES (1, 2), (2, 1)
                """);

        jdbcTemplate.update("""
                INSERT INTO benefits VALUES
                    (1, 'ACTIVE'),
                    (2, 'ACTIVE'),
                    (3, 'ACTIVE'),
                    (4, 'ACTIVE'),
                    (5, 'ACTIVE'),
                    (6, 'INACTIVE'),
                    (7, 'ACTIVE'),
                    (8, 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO eligibility_results VALUES
                    (1, 1, 2, 'ELIGIBLE', FALSE),
                    (1, 2, 2, 'CONDITIONAL', FALSE),
                    (1, 3, 2, 'INELIGIBLE', FALSE),
                    (1, 4, 2, 'INELIGIBLE', TRUE),
                    (1, 5, 1, 'ELIGIBLE', FALSE),
                    (1, 6, 2, 'ELIGIBLE', FALSE),
                    (2, 7, 1, 'ELIGIBLE', FALSE)
                """);

        var result = repository.findEligibilityCounts(1L);

        assertThat(result.totalCount()).isEqualTo(4L);
        assertThat(result.eligibleCount()).isEqualTo(1L);
        assertThat(result.conditionalCount()).isEqualTo(1L);
        assertThat(result.ineligibleCount()).isEqualTo(2L);
    }

    @Test
    void 판정_결과가_없으면_모든_건수가_0이다() {
        jdbcTemplate.update("""
                INSERT INTO users VALUES (1, 1)
                """);

        var result = repository.findEligibilityCounts(1L);

        assertThat(result.totalCount()).isZero();
        assertThat(result.eligibleCount()).isZero();
        assertThat(result.conditionalCount()).isZero();
        assertThat(result.ineligibleCount()).isZero();
    }

    @Test
    void 마지막_SERVICE_LIST_성공_시각을_반환한다() {
        jdbcTemplate.update("""
                INSERT INTO sync_logs VALUES
                    (1, 'SERVICE_LIST', 'SUCCESS',
                        '2026-11-20 10:00:00'),
                    (2, 'SERVICE_LIST', 'SUCCESS',
                        '2026-11-21 10:00:00'),
                    (3, 'SERVICE_LIST', 'PARTIAL',
                        '2026-11-22 10:00:00'),
                    (4, 'SERVICE_LIST', 'FAILED',
                        '2026-11-23 10:00:00'),
                    (5, 'OTHER', 'SUCCESS',
                        '2026-11-24 10:00:00')
                """);

        LocalDateTime result =
                repository.findBenefitDataUpdatedAt();

        assertThat(result).isEqualTo(
                LocalDateTime.of(2026, 11, 21, 10, 0)
        );
    }

    @Test
    void 정상_동기화_이력이_없으면_null을_반환한다() {
        jdbcTemplate.update("""
                INSERT INTO sync_logs VALUES
                    (1, 'SERVICE_LIST', 'PARTIAL',
                        '2026-11-22 10:00:00'),
                    (2, 'SERVICE_LIST', 'FAILED',
                        '2026-11-23 10:00:00')
                """);

        assertThat(repository.findBenefitDataUpdatedAt())
                .isNull();
    }
}