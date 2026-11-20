package com.bokdeongi.backend.extraction.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bokdeongi.backend.eligibility.service.EligibilityService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConditionExtractionServiceTest {

    /** DB 대신 메모리. */
    static class FakeStore extends ConditionStore {
        final List<ConditionSource> sources;
        final List<String> saved = new ArrayList<>();
        boolean saveOk = true;

        FakeStore(List<ConditionSource> sources) {
            super(null);
            this.sources = sources;
        }

        @Override
        public List<ConditionSource> loadSources(Collection<String> serviceIds) {
            return sources.stream().filter(s -> serviceIds.contains(s.serviceId())).toList();
        }

        @Override
        public boolean save(ConditionSource src, ConditionValidator.ServiceRules result) {
            saved.add(src.serviceId() + ":" + result.rules().size());
            return saveOk;
        }

        @Override
        public List<Long> loadUserIds() {
            return List.of(1L, 2L);
        }
    }

    static class FakeEligibility extends EligibilityService {
        int judged;

        FakeEligibility() {
            super(null, null);
        }

        @Override
        public int judgeUser(long userId) {
            judged++;
            return 0;
        }
    }

    private static ConditionSource source(long id, String serviceId) {
        ConditionSource c = ConditionFixtures.cheonan();
        return new ConditionSource(id, serviceId, c.title(), c.orgName(), c.targetText(), c.criteriaText(),
                c.contentText(), c.applyMethodText(), c.apiModifiedAt(), c.codeRules(), false);
    }

    private static ConditionBatch answerFor(String user) {
        List<ConditionBatch.Item> items = new ArrayList<>();
        for (String line : user.split("\n")) {
            if (line.startsWith("===== 공고")) {
                String id = line.substring(line.indexOf("service_id: ") + 12, line.indexOf(')'));
                items.add(new ConditionBatch.Item(id, ConditionFixtures.cheonanConditions()));
            }
        }
        return new ConditionBatch(items);
    }

    private ConditionExtractionService service(FakeStore store, ConditionExtractor extractor, FakeEligibility e,
            Path dir) {
        return new ConditionExtractionService(store, extractor, e, dir, () -> LocalDateTime.of(2026, 11, 20, 12, 0));
    }

    @Test
    void 세_건씩_두_번_부르고_저장한_뒤_회원을_다시_판정한다() throws Exception {
        List<ConditionSource> sources = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            sources.add(source(i, "S" + i));
        }
        FakeStore store = new FakeStore(sources);
        List<String> users = new ArrayList<>();
        ConditionExtractor extractor = (system, user) -> {
            users.add(user);
            return answerFor(user);
        };
        FakeEligibility e = new FakeEligibility();
        Path dir = Files.createTempDirectory("cond");
        ConditionExtractionService.Report r = service(store, extractor, e, dir)
                .run(List.of("S1", "S2", "S3", "S4", "S9", " S1 "));

        assertEquals(4, r.calls());   // 3건 + 1건, 각각 두 번
        assertEquals(users.get(0), users.get(1));
        assertEquals(List.of("S9"), r.notFound());
        assertEquals(4, r.saved());
        assertEquals(List.of("S1:6", "S2:6", "S3:6", "S4:6"), store.saved);
        assertEquals(2, e.judged);
        List<String> lines = Files.readAllLines(r.reviewFile(), StandardCharsets.UTF_8);
        assertTrue(lines.get(0).startsWith("﻿service_id,labeled_on,group,field"));
        assertEquals(1 + 4 * 6, lines.size());
        assertTrue(lines.get(1).startsWith("S1,2026-10-28,1,AGE,BETWEEN,60;64,60~64세 주민,"));
        assertTrue(r.reviewFile().getFileName().toString().startsWith("conditions-review-c3-20261120-120000"));
    }

    @Test
    void 검수_라벨이_있는_공고는_부르지_않는다() throws Exception {
        ConditionSource c = source(1, "S1");
        ConditionSource labeled = new ConditionSource(2, "S2", c.title(), c.orgName(), c.targetText(),
                c.criteriaText(), c.contentText(), c.applyMethodText(), c.apiModifiedAt(), c.codeRules(), true);
        FakeStore store = new FakeStore(List.of(c, labeled));
        List<String> users = new ArrayList<>();
        ConditionExtractor extractor = (system, user) -> {
            users.add(user);
            return answerFor(user);
        };
        ConditionExtractionService.Report r = service(store, extractor, new FakeEligibility(), Files.createTempDirectory("cond"))
                .run(List.of("S1", "S2"));
        assertEquals(1, r.skipped());
        assertTrue(users.stream().noneMatch(u -> u.contains("S2")));
    }

    @Test
    void 호출이_실패하거나_응답에_없으면_손대지_않는다() throws Exception {
        FakeStore store = new FakeStore(List.of(source(1, "S1"), source(2, "S2"), source(3, "S3"),
                source(4, "S4")));
        int[] n = {0};
        ConditionExtractor extractor = (system, user) -> {
            n[0]++;
            if (user.contains("S4")) {
                throw new IllegalStateException("429");
            }
            ConditionBatch b = answerFor(user);
            // 두 번째 응답에서 S2가 빠짐
            return n[0] == 2 ? new ConditionBatch(b.items().stream().filter(i -> !i.serviceId().equals("S2")).toList())
                    : b;
        };
        FakeEligibility e = new FakeEligibility();
        ConditionExtractionService.Report r = service(store, extractor, e, Files.createTempDirectory("cond"))
                .run(List.of("S1", "S2", "S3", "S4"));
        assertEquals(List.of("S1:6", "S3:6"), store.saved);
        assertEquals(2, r.failed());
        assertEquals(2, r.saved());
    }

    @Test
    void 저장이_안_되면_재판정하지_않는다() throws Exception {
        FakeStore store = new FakeStore(List.of(source(1, "S1")));
        store.saveOk = false;
        FakeEligibility e = new FakeEligibility();
        ConditionExtractionService.Report r = service(store, (s, u) -> answerFor(u), e,
                Files.createTempDirectory("cond")).run(List.of("S1"));
        assertEquals(0, r.saved());
        assertEquals(1, r.failed());
        assertEquals(0, e.judged);
    }

    @Test
    void 한국_날짜로_바꾼다() {
        assertEquals("2026-10-29",
                ConditionExtractionService.koreaDate(LocalDateTime.of(2026, 10, 28, 15, 30)).toString());
    }
}
