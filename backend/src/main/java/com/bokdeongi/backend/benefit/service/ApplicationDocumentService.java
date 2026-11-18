package com.bokdeongi.backend.benefit.service;

import com.bokdeongi.backend.benefit.dto.response.ContactResponse;
import com.bokdeongi.backend.benefit.dto.response.DocumentPrepResponse;
import com.bokdeongi.backend.benefit.dto.response.VisitGuideResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.entity.DocumentType;
import com.bokdeongi.backend.benefit.repository.DocumentTypeRepository;
import com.bokdeongi.backend.eligibility.store.JsonText;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 신청 도우미 ② 서류 준비(ASST-04)와 방문 준비물(ASST-06). LLM을 부르지 않는다.
 *
 * <p>신청 건(ASST-01, /api/applications/{id})이 생기면 컨트롤러가 신청 건 → Benefit을 꺼내 이 서비스를 부른다.
 * 그전까지는 Benefit(또는 원문 칸)을 받아 응답을 만드는 데까지만 한다.
 */
@Service
@Transactional(readOnly = true)
public class ApplicationDocumentService {

    private final DocumentTypeRepository documentTypeRepository;

    public ApplicationDocumentService(DocumentTypeRepository documentTypeRepository) {
        this.documentTypeRepository = documentTypeRepository;
    }

    /** ② 필요 서류: 공고 구비서류를 서류 목록과 맞춰 발급하는 곳별로 묶는다 */
    public DocumentPrepResponse documents(Benefit benefit) {
        return documents(benefit.getRequiredDocsText(), benefit.getContact());
    }

    public DocumentPrepResponse documents(String requiredDocsText, String contact) {
        List<DocumentPrepBuilder.MasterDoc> master = documentTypeRepository.findAll().stream()
                .map(ApplicationDocumentService::toMaster)
                .toList();
        DocumentPrepBuilder.Prep prep = DocumentPrepBuilder.build(requiredDocsText, master);
        List<DocumentPrepResponse.Group> groups = prep.groups().stream()
                .map(g -> new DocumentPrepResponse.Group(g.place(), g.url(), g.items().stream()
                        .map(i -> new DocumentPrepResponse.Item(i.doc().id(), i.doc().name(), i.sourceName(),
                                i.doc().issuer(), i.doc().onlineAvailable(), i.doc().issueMethod(),
                                i.doc().defaultValidityDays(), i.doc().alternatives(), i.doc().ocrSupported(),
                                i.orDocs().stream()
                                        .map(o -> new DocumentPrepResponse.Choice(o.id(), o.name(), o.issuer(),
                                                o.onlineAvailable(), o.issueUrl(), o.issueMethod()))
                                        .toList()))
                        .toList()))
                .toList();
        return new DocumentPrepResponse(groups, prep.forms(), prep.unknown(), contacts(contact), prep.total());
    }

    /** 방문 신청 준비물: 신분증 + 내가 낼 서류, 접수처, 문의처 */
    public VisitGuideResponse visitGuide(Benefit benefit) {
        return visitGuide(benefit.getApplyMethodText(), benefit.getRequiredDocsText(), benefit.getReceivingOrg(),
                benefit.getOrgName(), benefit.getContact());
    }

    public VisitGuideResponse visitGuide(String applyMethodText, String requiredDocsText, String receivingOrg,
                                         String orgName, String contact) {
        VisitChecklistBuilder.Checklist c = VisitChecklistBuilder.build(new VisitChecklistBuilder.Input(
                applyMethodText, requiredDocsText, receivingOrg, orgName, contact));
        return new VisitGuideResponse(c.visitApply(), c.items(), c.places(), c.placeFromOrg(), applyMethodText,
                contacts(contact));
    }

    static DocumentPrepBuilder.MasterDoc toMaster(DocumentType t) {
        List<String> aliases;
        try {
            aliases = JsonText.stringList(t.getAliases());
        } catch (IllegalArgumentException e) {
            aliases = null; // 별칭 칸이 깨져 있어도 이름으로는 맞춘다
        }
        return new DocumentPrepBuilder.MasterDoc(t.getId(), t.getName(), aliases == null ? List.of() : aliases,
                t.getIssuer(), Boolean.TRUE.equals(t.getOnlineAvailable()), t.getIssueUrl(), t.getIssueMethod(),
                t.getDefaultValidityDays(), t.getAlternatives(), Boolean.TRUE.equals(t.getOcrSupported()));
    }

    private static List<ContactResponse> contacts(String contact) {
        return VisitChecklistBuilder.contacts(contact).stream()
                .map(c -> new ContactResponse(c.name(), c.phone()))
                .toList();
    }
}
