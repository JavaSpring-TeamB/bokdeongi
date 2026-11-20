package com.bokdeongi.backend.benefit.assist;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** ① 자격 확인에 보여 줄 공고 원문. */
@Repository
public class EligibilityCheckStore {

    private final JdbcTemplate jdbc;

    public EligibilityCheckStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<EligibilityCheckView.Source> source(long benefitId) {
        return jdbc.query("SELECT title, service_field, target_text, criteria_text FROM benefits WHERE id = ?",
                (rs, n) -> new EligibilityCheckView.Source(rs.getString("title"), rs.getString("service_field"),
                        rs.getString("target_text"), rs.getString("criteria_text")), benefitId)
                .stream().findFirst();
    }
}
