package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bokdeongi.backend.benefit.dto.response.ContactResponse;
import com.bokdeongi.backend.benefit.dto.response.DocumentPrepResponse;
import com.bokdeongi.backend.benefit.dto.response.VisitGuideResponse;
import com.bokdeongi.backend.benefit.entity.DocumentType;
import com.bokdeongi.backend.benefit.repository.DocumentTypeRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** document_types 테이블(H2)에서 서류 목록을 읽어 ② 필요 서류 응답을 만드는지 확인한다. */
@SpringBootTest
class ApplicationDocumentServiceTest {

    @Autowired
    private ApplicationDocumentService service;

    @Autowired
    private DocumentTypeRepository repository;

    @BeforeEach
    void seed() {
        repository.save(new DocumentType("주민등록등본", "[\"등본\",\"주민등록표 등본\"]", "읍·면·동 행정복지센터", true,
                "https://www.gov.kr", "정부24 무료 발급", 90, null, true));
        repository.save(new DocumentType("가족관계증명서", "[]", "대법원 가족관계등록", true,
                "https://efamily.scourt.go.kr", "전자가족관계등록시스템", 90, null, false));
        repository.save(new DocumentType("통장사본", "[\"통장 사본\"]", "본인 거래 은행", true, null, "은행 앱", null, null, false));
    }

    @AfterEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void 서류를_발급하는_곳별로_묶고_서식과_모르는_서류를_나눈다() {
        DocumentPrepResponse r = service.documents(
                "주민등록표 등본 1부, 가족관계증명서, 청년 본인의 통장 사본, 지원신청서, 임대차계약서",
                "주거복지과/02-123-4567");

        assertEquals(List.of("정부24", "대법원 전자가족관계등록시스템", "본인 거래 은행"),
                r.groups().stream().map(DocumentPrepResponse.Group::place).toList());
        DocumentPrepResponse.Item 등본 = r.groups().get(0).items().get(0);
        assertEquals("주민등록등본", 등본.name());
        assertTrue(등본.ocrSupported());
        assertEquals(90, 등본.validityDays());
        assertEquals(List.of("지원신청서"), r.forms());
        assertEquals(List.of("임대차계약서"), r.unknown());
        assertEquals(List.of(new ContactResponse("주거복지과", "02-123-4567")), r.contacts());
        assertEquals(5, r.total());
    }

    @Test
    void 방문_준비물은_신분증과_서류_접수처_문의처() {
        VisitGuideResponse r = service.visitGuide("○ 방문 신청\n - 주민센터 : 주소지 읍면동 주민센터 방문 신청",
                "주민등록등본, 통장사본", null, "서울특별시 광진구", "복지정책과/02-450-0000");

        assertTrue(r.visitApply());
        assertEquals(List.of("신분증", "주민등록등본", "통장사본"), r.items());
        assertEquals(List.of("주소지 읍면동 주민센터"), r.places());
        assertEquals(List.of(new ContactResponse("복지정책과", "02-450-0000")), r.contacts());
    }
}
