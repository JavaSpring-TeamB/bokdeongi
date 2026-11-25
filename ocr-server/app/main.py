"""복덩이 OCR 서버 (C · ASST-03). 백엔드만 부르는 내부 서버다.

실행: uvicorn app.main:app --host 127.0.0.1 --port 8001

- GET  /health          서버 확인
- POST /check           서류 검토: 종류·발급일·필수 항목 판정 (원본·텍스트 저장 안 함)
- POST /text            마스킹된 텍스트만 돌려줌 (EASY-02 고지서 사진용). 개인 서류(등본 등)는 422로 막는다

서류 처리 동의(DOC_PROCESS) 확인과 doc_checks 저장은 백엔드가 한다.
"""
from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from . import engine
from .documents import SPEC_BY_CODE, Result, check, personal_document
from .masking import MaskingError, mask_lines

MAX_BYTES = 10 * 1024 * 1024  # 10MB
MIN_SCORE = 0.6               # 평균 인식 신뢰도가 이보다 낮으면 재촬영

# 확장자를 믿지 않고 파일 앞 바이트로 형식을 본다
MAGIC = {b"\x89PNG": ".png", b"\xff\xd8\xff": ".jpg", b"%PDF": ".pdf"}

app = FastAPI(title="bokdeongi-ocr", version="0.1.0")


@app.get("/health")
def health():
    return {"status": "ok"}


async def _read_upload(file: UploadFile) -> tuple[bytes, str]:
    data = await file.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise HTTPException(413, detail={"code": "FILE_TOO_LARGE", "message": "10MB 이하 파일만 올릴 수 있어요."})
    for magic, suffix in MAGIC.items():
        if data.startswith(magic):
            return data, suffix
    raise HTTPException(415, detail={"code": "UNSUPPORTED_FORMAT", "message": "JPG, PNG, PDF만 올릴 수 있어요."})


def _ocr_masked(data: bytes, suffix: str) -> tuple[list[str] | None, str]:
    """OCR → 바로 마스킹. 실패하면 (None, 이유)."""
    out = engine.read(data, suffix)
    if out.mean_score < MIN_SCORE:
        return None, "사진이 흐려서 글자를 읽기 어려워요. 다시 찍어 주세요."
    try:
        return mask_lines(out.lines), ""
    except MaskingError:
        return None, "개인정보를 가리지 못해서 처리를 멈췄어요. 다시 찍어 주세요."


@app.post("/check")
async def check_document(
    file: UploadFile = File(...),
    expected_type: str | None = Form(None),
    max_age_days: int | None = Form(None),
):
    if expected_type and expected_type not in SPEC_BY_CODE:
        raise HTTPException(400, detail={"code": "UNKNOWN_DOC_TYPE", "message": f"OCR 대상이 아닌 서류: {expected_type}"})
    data, suffix = await _read_upload(file)
    try:
        lines, why = _ocr_masked(data, suffix)
    finally:
        del data
    if lines is None:
        return _response(Result.UNREADABLE, reason=why)
    c = check(lines, expected_type=expected_type, max_age_days=max_age_days)
    return _response(c.result, c.detected_type, c.issue_date, c.found_fields, c.missing_fields, c.reason)


@app.post("/text")
async def masked_text(file: UploadFile = File(...)):
    data, suffix = await _read_upload(file)
    try:
        lines, why = _ocr_masked(data, suffix)
    finally:
        del data
    if lines is None:
        raise HTTPException(422, detail={"code": "UNREADABLE", "message": why})
    name = personal_document(lines)
    if name:  # 서류 내용은 LLM에 보내지 않는다(D-78). 글자는 돌려주지 않는다
        raise HTTPException(422, detail={
            "code": "PERSONAL_DOCUMENT",
            "message": f"{name} 같은 개인 서류는 쉬운 말 번역기에 올릴 수 없어요. 서류 확인은 신청 도우미에서 해 주세요.",
        })
    return {"lines": lines}


def _response(result, detected_type=None, issue_date=None, found=None, missing=None, reason=""):
    return {
        "result": result.value,
        "detectedType": detected_type,
        "issueDate": issue_date.isoformat() if issue_date else None,
        "foundFields": found or [],
        "missingFields": missing or [],
        "reason": reason,
    }
