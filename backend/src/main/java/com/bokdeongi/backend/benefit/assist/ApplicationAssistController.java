package com.bokdeongi.backend.benefit.assist;

import com.bokdeongi.backend.benefit.dto.response.ApplyGuideResponse;
import com.bokdeongi.backend.benefit.dto.response.DocumentCheckResponse;
import com.bokdeongi.backend.benefit.dto.response.MissingDocumentResponse;
import com.bokdeongi.backend.benefit.recommend.RecommendController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** AI 신청 도우미 ②·③·④ (ASST-03·04·05). 신청 건은 POST /api/applications(A)로 먼저 만든다 */
@Tag(name = "AI 신청 도우미")
@RestController
@RequestMapping("/api/applications/{id}")
@SecurityRequirement(name = "bearerAuth")
public class ApplicationAssistController {

    private final ApplicationAssistService service;

    public ApplicationAssistController(ApplicationAssistService service) {
        this.service = service;
    }

    @Operation(summary = "② 필요 서류·준비 상태",
            description = "발급처별 서류 묶음 + 서류 키별 상태(prepared, reviewed, result). 키는 서류 종류 id, 양식은 form:0")
    @GetMapping("/documents")
    public AssistDto.DocumentsResponse documents(Authentication authentication, @PathVariable long id) {
        return service.documents(RecommendController.userId(authentication), id);
    }

    @Operation(summary = "② 서류 준비 완료 체크",
            description = "body {\"prepared\": true}. OCR로 검토하지 않는 서류는 준비 체크가 곧 검토 완료")
    @PatchMapping("/documents/{documentKey}")
    public AssistDto.DocumentsResponse prepare(Authentication authentication, @PathVariable long id,
                                               @PathVariable String documentKey,
                                               @RequestBody AssistDto.PrepareRequest request) {
        boolean prepared = request == null || request.prepared() == null || request.prepared();
        return service.prepare(RecommendController.userId(authentication), id, documentKey, prepared);
    }

    @Operation(summary = "③ 서류 올려 검토 (로컬 OCR만)",
            description = "multipart: file(JPG·PNG·PDF, 10MB), documentTypeId. 서류 처리 동의(DOC_PROCESS) 필요. "
                    + "파일은 저장하지 않고 종류·발급일·필수 항목 결과만 남긴다. 서류 내용은 LLM에 보내지 않는다(D-78). "
                    + "result: READY / MISSING / INVALID / RETRY(다시 올리기, 저장 안 함)")
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentCheckResponse upload(Authentication authentication, @PathVariable long id,
                                        @RequestParam("documentTypeId") Long documentTypeId,
                                        @RequestPart("file") MultipartFile file) {
        return service.upload(RecommendController.userId(authentication), id, documentTypeId, file);
    }

    @Operation(summary = "③ 부족 서류 보완 안내", description = "아직 준비·검토가 안 된 서류를 발급처별로. 다 되면 allReady=true")
    @GetMapping("/missing")
    public MissingDocumentResponse missing(Authentication authentication, @PathVariable long id) {
        return service.missing(RecommendController.userId(authentication), id);
    }

    @Operation(summary = "④ 신청 방법 안내", description = "신청 방법 단계 카드·신청 링크·이름·연락처 등 입력값 복사 카드")
    @GetMapping("/guide")
    public ApplyGuideResponse guide(Authentication authentication, @PathVariable long id) {
        return service.guide(RecommendController.userId(authentication), id);
    }
}
