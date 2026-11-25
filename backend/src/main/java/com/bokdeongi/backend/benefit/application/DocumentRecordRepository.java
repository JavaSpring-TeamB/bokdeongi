package com.bokdeongi.backend.benefit.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.ResultSetExtractor;

/**
 * 회원의 서류 기억 기록을 관리하는 Repository.
 *
 * users 테이블의 doc_records JSON 데이터를 조회,
 * 저장 및 삭제하며 사용자 행 잠금 기능을 제공한다.
 */
@Repository
public class DocumentRecordRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 회원에게 저장된 서류 기록 JSON을 조회한다.
     */
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

    /**
     * 회원의 서류 기록 JSON을 저장한다.
     *
     * H2와 운영 DB의 JSON 저장 문법 차이를 구분하여 처리한다.
     */
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

    /**
     * 회원에게 저장된 모든 서류 기억 기록을 삭제한다.
     */
    public int deleteRecords(Long userId) {
        return jdbcTemplate.update(
                "UPDATE users SET doc_records = NULL WHERE id = ?",
                userId
        );
    }
}