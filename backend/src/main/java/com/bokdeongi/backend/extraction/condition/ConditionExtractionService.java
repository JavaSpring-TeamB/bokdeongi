package com.bokdeongi.backend.extraction.condition;

import com.bokdeongi.backend.eligibility.service.EligibilityService;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * DATA-03 판정 조건 추출. 지정한 공고만 돈다 (정답셋·시연 페르소나 혜택, D-93). 조회 시 실시간 생성은 하지 않는다.
 *
 * <ol>
 *   <li>공고 3건씩 묶어 같은 요청을 두 번 보낸다 (D-60, D-97). 호출 수 = 2 × ⌈건수/3⌉</li>
 *   <li>공고마다 검증해(D-59·D-61) LLM 규칙으로 저장한다. 검수 라벨(MANUAL)이 있는 공고는 건너뛴다.
 *       기본은 저장하지 않고 검수 파일만 쓴다(app.conditions.save=false). 시연 규칙은 검수를 거쳐 수동 규칙으로 들어가서다 (D-99)</li>
 *   <li>B가 검수할 파일을 쓴다. 검수한 줄은 수동 규칙으로 올린다 ({@link ConditionReviewFile})</li>
 *   <li>하나라도 저장했으면 회원 전체를 다시 판정한다</li>
 * </ol>
 * 호출이 실패하거나 응답에 그 공고가 없으면 그 공고는 손대지 않는다 (지원조건 코드 규칙 그대로).
 */
@Service
public class ConditionExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ConditionExtractionService.class);
    static final int BATCH_SIZE = 3;
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ConditionStore store;
    private final ConditionExtractor extractor;
    private final EligibilityService eligibility;
    private final Path reviewDir;
    private final boolean save;
    private final Supplier<LocalDateTime> now;

    @Autowired
    public ConditionExtractionService(ConditionStore store, ConditionExtractor extractor,
            EligibilityService eligibility, @Value("${app.conditions.review-dir:conditions-review}") String reviewDir,
            @Value("${app.conditions.save:false}") boolean save) {
        this(store, extractor, eligibility, Path.of(reviewDir), save, () -> LocalDateTime.now(KOREA));
    }

    ConditionExtractionService(ConditionStore store, ConditionExtractor extractor, EligibilityService eligibility,
            Path reviewDir, boolean save, Supplier<LocalDateTime> now) {
        this.store = store;
        this.extractor = extractor;
        this.eligibility = eligibility;
        this.reviewDir = reviewDir;
        this.save = save;
        this.now = now;
    }

    /**
     * @param requested 요청한 공고 수
     * @param skipped   검수 라벨이 있어 건너뛴 수
     * @param saved     LLM 규칙을 저장한 수 (저장을 끈 실행은 0)
     * @param reviewed  검수 파일에 줄을 남긴 공고 수
     * @param failed    호출 실패·응답 누락·저장 중 원문 변경으로 못 한 수
     * @param calls     LLM 호출 수
     */
    public record Report(int requested, List<String> notFound, int skipped, int reviewed, int saved, int failed,
            int calls, boolean saveEnabled, Path reviewFile, int rejudgedUsers) {
    }

    public Report run(List<String> serviceIds) {
        Set<String> ids = new LinkedHashSet<>();
        for (String id : serviceIds) {
            if (id != null && !id.isBlank()) {
                ids.add(id.strip());
            }
        }
        List<ConditionSource> found = store.loadSources(ids);
        Set<String> foundIds = new LinkedHashSet<>();
        found.forEach(s -> foundIds.add(s.serviceId()));
        List<String> notFound = ids.stream().filter(id -> !foundIds.contains(id)).toList();

        List<ConditionSource> targets = found.stream().filter(s -> !s.manualCurrent()).toList();
        int skipped = found.size() - targets.size();
        int reviewed = 0;
        int saved = 0;
        int failed = 0;
        int calls = 0;
        List<ConditionValidator.ReviewLine> review = new ArrayList<>();
        Map<String, LocalDate> labeledOn = new HashMap<>();
        String system = ConditionPrompt.system();

        for (int i = 0; i < targets.size(); i += BATCH_SIZE) {
            List<ConditionSource> chunk = targets.subList(i, Math.min(targets.size(), i + BATCH_SIZE));
            String user = ConditionPrompt.user(chunk);
            Map<String, ConditionBatch.Item> first;
            Map<String, ConditionBatch.Item> second;
            try {
                calls++;
                first = byService(extractor.extract(system, user));
                calls++;
                second = byService(extractor.extract(system, user));
            } catch (RuntimeException e) {
                log.warn("조건 추출 호출 실패 {}: {}", chunk.stream().map(ConditionSource::serviceId).toList(),
                        e.getMessage());
                failed += chunk.size();
                continue;
            }
            for (ConditionSource src : chunk) {
                ConditionBatch.Item a = first.get(src.serviceId());
                ConditionBatch.Item b = second.get(src.serviceId());
                if (a == null || b == null) {
                    log.warn("조건 추출 응답에 공고 {} 없음", src.serviceId());
                    failed++;
                    continue;
                }
                ConditionValidator.ServiceRules result = ConditionValidator.validate(src, a.conditions(),
                        b.conditions());
                review.addAll(result.review());
                reviewed++;
                if (src.apiModifiedAt() != null) {
                    labeledOn.put(src.serviceId(), koreaDate(src.apiModifiedAt()));
                }
                if (!save) {
                    continue;
                }
                if (store.save(src, result)) {
                    saved++;
                } else {
                    log.warn("공고 {}: 그사이 원문이 바뀌었거나 검수 라벨이 있어 저장 안 함", src.serviceId());
                    failed++;
                }
            }
        }

        Path file = null;
        if (!review.isEmpty()) {
            file = reviewDir.resolve("conditions-review-" + ConditionPrompt.VERSION + "-" + FILE_TIME.format(now.get())
                    + ".csv");
            ConditionReviewFile.write(file, review, labeledOn);
        }
        int rejudged = saved > 0 ? rejudgeAll() : 0;
        Report report = new Report(ids.size(), notFound, skipped, reviewed, saved, failed, calls, save, file,
                rejudged);
        log.info("조건 추출(DATA-03, {}) 결과: {}", ConditionPrompt.VERSION, report);
        return report;
    }

    private static Map<String, ConditionBatch.Item> byService(ConditionBatch batch) {
        Map<String, ConditionBatch.Item> out = new HashMap<>();
        for (ConditionBatch.Item item : batch.items()) {
            if (item != null && item.serviceId() != null) {
                out.putIfAbsent(item.serviceId().strip(), item);
            }
        }
        return out;
    }

    /** benefits.api_modified_at은 UTC로 저장된다 (DATA-01). 라벨 날짜는 한국 날짜 (D-93) */
    static LocalDate koreaDate(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA).toLocalDate();
    }

    private int rejudgeAll() {
        int done = 0;
        for (Long userId : store.loadUserIds()) {
            try {
                eligibility.judgeUser(userId);
                done++;
            } catch (RuntimeException e) {
                log.warn("회원 {} 재판정 실패: {}", userId, e.getMessage());
            }
        }
        return done;
    }
}
