package com.bokdeongi.backend.benefit.service;

import com.bokdeongi.backend.benefit.dto.response.DocumentPrepResponse;
import com.bokdeongi.backend.benefit.dto.response.MissingDocumentResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * AI 신청 도우미 ③ 부족 서류 보완 (ASST-04, AS-08). LLM을 부르지 않는다.
 *
 * <p>② 서류 준비(ApplicationDocumentService.documents)의 발급처 묶음에서, 서류 상태가 READY·NOT_REQUIRED가 아닌 서류만 남긴다.
 * "A 또는 B"는 둘 중 하나라도 READY면 준비된 것으로 본다.
 *
 * <p>서류 상태는 user_benefits.doc_checks(ASST-01, A)에 있다. 그전까지는 상태 맵을 받아 응답을 만드는 데까지만 한다.
 * 상태가 없는 서류는 MISSING으로 본다.
 */
@Service
public class MissingDocumentService {

    public static final String NOT_REQUIRED = "NOT_REQUIRED";
    static final String NOT_YET = "아직 준비하지 않았어요";
    static final String VISIT_TIP = "신분증을 챙겨서 발급하는 곳에 방문해요";

    /**
     * 서류 하나의 상태 (doc_checks 한 칸).
     *
     * @param result READY / MISSING / INVALID / NOT_REQUIRED
     * @param reason 서류 검토 이유 문구. 없으면 null
     */
    public record DocStatus(String result, String reason) {
    }

    private final ApplicationDocumentService documentService;

    public MissingDocumentService(ApplicationDocumentService documentService) {
        this.documentService = documentService;
    }

    /** @param statuses 서류 종류 id → 상태. null이면 전부 MISSING */
    public MissingDocumentResponse missing(Benefit benefit, Map<Long, DocStatus> statuses) {
        return missing(documentService.documents(benefit), statuses);
    }

    static MissingDocumentResponse missing(DocumentPrepResponse prep, Map<Long, DocStatus> statuses) {
        Map<Long, DocStatus> st = statuses == null ? Map.of() : statuses;
        List<MissingDocumentResponse.Group> groups = new ArrayList<>();
        for (DocumentPrepResponse.Group g : prep.groups()) {
            List<MissingDocumentResponse.Doc> docs = new ArrayList<>();
            boolean online = true;
            for (DocumentPrepResponse.Item item : g.items()) {
                if (ready(item.documentTypeId(), st) || item.orDocs().stream().anyMatch(o -> ready(o.documentTypeId(), st))) {
                    continue;
                }
                DocStatus s = st.get(item.documentTypeId());
                String result = s == null || s.result() == null ? DocumentCheckService.MISSING : s.result();
                String reason = s == null || s.reason() == null || s.reason().isBlank() ? NOT_YET : s.reason();
                docs.add(new MissingDocumentResponse.Doc(item.documentTypeId(), item.name(), item.sourceName(), result,
                        reason, item.issueMethod(), item.alternatives(),
                        item.orDocs().stream().map(DocumentPrepResponse.Choice::name).toList(), item.ocrSupported()));
                online &= item.onlineAvailable();
            }
            if (!docs.isEmpty()) {
                groups.add(new MissingDocumentResponse.Group(g.place(), online, g.url(), online ? null : VISIT_TIP,
                        List.copyOf(docs)));
            }
        }
        groups.sort((a, b) -> b.documents().size() - a.documents().size());
        return new MissingDocumentResponse(List.copyOf(groups), prep.forms(), prep.unknown(), groups.isEmpty());
    }

    private static boolean ready(Long id, Map<Long, DocStatus> st) {
        DocStatus s = st.get(id);
        return s != null && (DocumentCheckService.READY.equals(s.result()) || NOT_REQUIRED.equals(s.result()));
    }
}
