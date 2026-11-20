package com.bokdeongi.backend.extraction.condition;

import com.bokdeongi.backend.eligibility.Operator;
import com.bokdeongi.backend.eligibility.Rule;
import com.bokdeongi.backend.eligibility.label.LabelRuleCatalog;
import com.bokdeongi.backend.eligibility.store.JsonText;
import com.bokdeongi.backend.sync.BenefitMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** DATA-03 DB 읽기·쓰기 (benefits 원문, benefit_rules). */
@Repository
public class ConditionStore {

    private static final int CHUNK = 500;

    private final JdbcTemplate jdbc;

    public ConditionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** service_id로 공고 원문과 코드 규칙을 읽는다. 없는 ID는 빠진다 */
    public List<ConditionSource> loadSources(Collection<String> serviceIds) {
        List<Object[]> rows = new ArrayList<>();
        List<String> ids = new ArrayList<>(serviceIds);
        for (int i = 0; i < ids.size(); i += CHUNK) {
            List<String> chunk = ids.subList(i, Math.min(ids.size(), i + CHUNK));
            String in = String.join(", ", Collections.nCopies(chunk.size(), "?"));
            rows.addAll(jdbc.query("""
                    SELECT b.id, b.service_id, b.title, b.org_name, b.target_text, b.criteria_text, b.content_text,
                           b.apply_method_text, b.api_modified_at,
                           (SELECT COUNT(*) FROM benefit_rules r WHERE r.benefit_id = b.id AND r.source = 'MANUAL'
                               AND r.source_modified_at = b.api_modified_at) AS manual_current
                    FROM benefits b WHERE b.service_id IN (""" + in + ")",
                    (rs, n) -> {
                        Timestamp t = rs.getTimestamp("api_modified_at");
                        return new Object[] {rs.getLong("id"), rs.getString("service_id"), rs.getString("title"),
                                rs.getString("org_name"), rs.getString("target_text"), rs.getString("criteria_text"),
                                rs.getString("content_text"), rs.getString("apply_method_text"),
                                t == null ? null : t.toLocalDateTime(), rs.getInt("manual_current") > 0};
                    }, chunk.toArray()));
        }
        Map<Long, List<Rule>> code = codeRules(rows.stream().map(r -> (Long) r[0]).toList());
        Map<String, ConditionSource> byService = new LinkedHashMap<>();
        for (Object[] r : rows) {
            long id = (Long) r[0];
            byService.put((String) r[1], new ConditionSource(id, (String) r[1], (String) r[2], (String) r[3],
                    (String) r[4], (String) r[5], (String) r[6], (String) r[7], (LocalDateTime) r[8],
                    code.getOrDefault(id, List.of()), (Boolean) r[9]));
        }
        // 요청한 순서대로
        List<ConditionSource> out = new ArrayList<>();
        for (String id : ids) {
            ConditionSource s = byService.remove(id);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    private Map<Long, List<Rule>> codeRules(List<Long> benefitIds) {
        Map<Long, List<Rule>> out = new HashMap<>();
        for (int i = 0; i < benefitIds.size(); i += CHUNK) {
            List<Long> chunk = benefitIds.subList(i, Math.min(benefitIds.size(), i + CHUNK));
            String in = String.join(", ", Collections.nCopies(chunk.size(), "?"));
            jdbc.query("""
                    SELECT benefit_id, rule_group, field, operator, `value`, source_quote FROM benefit_rules
                    WHERE source = 'CODE' AND benefit_id IN (""" + in + ") ORDER BY benefit_id, rule_group, id",
                    (rs, n) -> {
                        Rule rule = new Rule(null, rs.getString("field"), Operator.valueOf(rs.getString("operator")),
                                JsonText.stringList(rs.getString("value")), "CODE", rs.getString("source_quote"), null,
                                rs.getInt("rule_group"));
                        out.computeIfAbsent(rs.getLong("benefit_id"), k -> new ArrayList<>()).add(rule);
                        return null;
                    }, chunk.toArray());
        }
        return out;
    }

    /**
     * 공고 하나의 규칙을 LLM 규칙으로 통째로 바꾼다 (코드 규칙도 지움, 원문 우선 — 검수 라벨 D-93과 같은 방식).
     * 그사이 동기화로 공고가 바뀌었거나(수정일시가 다름) 검수 라벨이 들어왔으면 아무것도 안 바꾸고 false.
     * 원문이 다시 바뀌면 동기화가 코드 규칙을 다시 넣고 이 규칙은 낡은 것으로 지운다 (source_modified_at).
     */
    @Transactional
    public boolean save(ConditionSource src, ConditionValidator.ServiceRules result) {
        List<Map<String, Object>> unparsed = new ArrayList<>();
        for (LabelRuleCatalog.Unparsed u : result.unparsed()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sourceQuote", u.sourceQuote());
            m.put("ruleGroup", u.ruleGroup());
            unparsed.add(m);
        }
        Timestamp modified = src.apiModifiedAt() == null ? null : Timestamp.valueOf(src.apiModifiedAt());
        // 행을 잠그고 확인한다 (UPDATE 결과 행 수는 드라이버 설정에 따라 '바뀐 행'만 셀 수 있어서 쓰지 않는다)
        List<Long> current = jdbc.query("""
                SELECT b.id FROM benefits b
                WHERE b.id = ? AND b.api_modified_at <=> ?
                  AND NOT EXISTS (SELECT 1 FROM benefit_rules r WHERE r.benefit_id = b.id AND r.source = 'MANUAL'
                      AND r.source_modified_at <=> b.api_modified_at)
                FOR UPDATE""", (rs, n) -> rs.getLong("id"), src.benefitId(), modified);
        if (current.isEmpty()) {
            return false;
        }
        jdbc.update("""
                UPDATE benefits SET rules_status = ?, unparsed_conditions = ?,
                    target_scope = CASE WHEN ? THEN 'CORP' ELSE target_scope END
                WHERE id = ?""",
                result.rulesStatus(), unparsed.isEmpty() ? null : BenefitMapper.objects(unparsed), result.outOfScope(),
                src.benefitId());
        jdbc.update("DELETE FROM benefit_rules WHERE benefit_id = ?", src.benefitId());
        for (Rule r : result.rules()) {
            jdbc.update("""
                    INSERT INTO benefit_rules (benefit_id, rule_group, field, operator, `value`, source, source_quote,
                        confidence, verified, source_modified_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?)""",
                    src.benefitId(), r.group(), r.field(), r.operator().name(), JsonText.writeStrings(r.values()),
                    r.source(), r.sourceQuote(), r.confidence(), modified);
        }
        return true;
    }

    public List<Long> loadUserIds() {
        return jdbc.query("SELECT id FROM users", (rs, n) -> rs.getLong("id"));
    }
}
