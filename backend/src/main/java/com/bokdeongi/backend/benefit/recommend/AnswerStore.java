package com.bokdeongi.backend.benefit.recommend;

import com.bokdeongi.backend.eligibility.store.JsonText;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** users.answers 읽기·쓰기. 답이 바뀌면 profile_version을 올려 재판정 대상이 되게 한다 (PROF-05). */
@Repository
public class AnswerStore {

    private final JdbcTemplate jdbc;

    public AnswerStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 동시에 두 요청이 답을 합칠 때 하나가 덮어쓰지 않게 행을 잠근다 (트랜잭션 안에서 호출) */
    public Map<String, String> lockAnswers(long userId) {
        List<String> rows = jdbc.query("SELECT answers FROM users WHERE id = ? FOR UPDATE",
                (rs, n) -> rs.getString("answers"), userId);
        if (rows.isEmpty()) {
            throw new RecommendException("회원을 찾을 수 없습니다");
        }
        return new LinkedHashMap<>(JsonText.stringMap(rows.get(0)));
    }

    public void saveAnswers(long userId, Map<String, String> answers) {
        StringBuilder json = new StringBuilder();
        JsonText.writeObject(json, answers);
        jdbc.update("UPDATE users SET answers = ?, profile_version = profile_version + 1 WHERE id = ?",
                json.toString(), userId);
    }
}
