package com.bokdeongi.backend.benefit.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.ResultSetExtractor;

@Repository
public class DocumentRecordRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String findRecords(Long userId) {
        return jdbcTemplate.query(
                "SELECT doc_records FROM users WHERE id = ?",
                rs -> rs.next() ? rs.getString("doc_records") : null,
                userId
        );
    }

    /**
     * 서류 기록 변경 전 사용자 행을 잠근다.
     * 반드시 트랜잭션 내부에서 호출해야 한다.
     */
    public boolean lockUser(Long userId) {
        return jdbcTemplate.query(
                "SELECT id FROM users WHERE id = ? FOR UPDATE",
                (ResultSetExtractor<Boolean>) rs -> rs.next(),
                userId
        );
    }

    public int saveRecords(Long userId, String recordsJson) {
        String databaseName = jdbcTemplate.execute(
                (java.sql.Connection connection) ->
                        connection.getMetaData().getDatabaseProductName()
        );

        String sql = "H2".equalsIgnoreCase(databaseName)
                ? "UPDATE users SET doc_records = ? FORMAT JSON WHERE id = ?"
                : "UPDATE users SET doc_records = ? WHERE id = ?";

        return jdbcTemplate.update(sql, recordsJson, userId);
    }

    public int deleteRecords(Long userId) {
        return jdbcTemplate.update(
                "UPDATE users SET doc_records = NULL WHERE id = ?",
                userId
        );
    }
}