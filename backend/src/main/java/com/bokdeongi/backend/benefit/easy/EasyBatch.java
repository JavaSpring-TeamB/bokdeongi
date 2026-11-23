package com.bokdeongi.backend.benefit.easy;

import com.bokdeongi.backend.benefit.dto.response.EasyResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 시연 공고 쉬운 말 미리 만들기 (EASY-01, D-09 캐시 방식).
 *
 * <p>서비스ID마다 EasyTextService를 한 번씩 부른다. 이미 같은 원문으로 만든 글이 있으면 LLM을 부르지 않고 그대로 둔다.
 * 만든 글은 benefits의 쉬운 말 칸에 저장되고, 상세 화면(easy=true)은 저장된 글을 바로 쓴다.
 */
@Component
public class EasyBatch {

    private static final Logger log = LoggerFactory.getLogger(EasyBatch.class);

    private final BenefitRepository benefitRepository;
    private final EasyTextService easyTextService;

    public EasyBatch(BenefitRepository benefitRepository, EasyTextService easyTextService) {
        this.benefitRepository = benefitRepository;
        this.easyTextService = easyTextService;
    }

    /**
     * @param requested 요청한 공고 수
     * @param notFound  benefits에 없는 서비스ID
     * @param reused    이미 만든 글이 있어 LLM을 안 부른 수
     * @param done      새로 만들어 검사를 통과한 수
     * @param failed    검사 탈락이나 호출 실패로 원문만 보여 줄 수
     * @param failedIds 실패한 서비스ID (다시 돌리거나 검수할 때 참고)
     */
    public record Report(int requested, List<String> notFound, int reused, int done, int failed,
            List<String> failedIds) {
    }

    public Report run(List<String> serviceIds) {
        Set<String> ids = new LinkedHashSet<>();
        for (String id : serviceIds) {
            if (id != null && !id.isBlank()) {
                ids.add(id.strip());
            }
        }
        List<String> notFound = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        int reused = 0;
        int done = 0;
        for (String id : ids) {
            Optional<Benefit> found = benefitRepository.findByServiceId(id);
            if (found.isEmpty()) {
                notFound.add(id);
                continue;
            }
            Benefit benefit = found.get();
            boolean cached = benefit.getEasyText() != null && benefit.getAiGeneratedAt() != null
                    && Objects.equals(benefit.getAiSourceModifiedAt(), benefit.getApiModifiedAt());
            EasyResponse r;
            try {
                r = easyTextService.easy(benefit);
            } catch (RuntimeException e) {
                log.warn("쉬운 말 미리 만들기 실패 {}: {}", id, e.getMessage());
                failedIds.add(id);
                continue;
            }
            if (!EasyTextService.DONE.equals(r.status())) {
                failedIds.add(id);
            } else if (cached) {
                reused++;
            } else {
                done++;
            }
        }
        Report report = new Report(ids.size(), notFound, reused, done, failedIds.size(), failedIds);
        log.info("쉬운 말 미리 만들기(EASY-01, {}) 결과: {}", EasyPrompt.VERSION, report);
        return report;
    }
}
