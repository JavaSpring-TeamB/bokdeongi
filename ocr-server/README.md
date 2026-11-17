# ocr-server

서류 사진·PDF를 **로컬에서** 인식하는 OCR 서버 (AI 튜닝 담당, ASST-03 서류 검토).

- 엔진: PaddleOCR 3.7.0 (한국어 포함 다국어 PP-OCRv5), 대안 Tesseract
- 역할: 서류 종류·발급일·필수 항목 추출에 필요한 텍스트 인식, 주민등록번호 마스킹
- 원칙: 서류 이미지는 이 서버 밖(외부 API, LLM)으로 보내지 않는다. 마스킹된 텍스트만 백엔드로 돌려준다
- 포트: `.env`의 `OCR_SERVER_URL` (기본 8001)
- 서버: FastAPI. 백엔드만 부르는 내부 서버라 `127.0.0.1`에서만 연다

## 처리 원칙

- 원본 파일은 임시 파일로만 쓰고 OCR 직후(실패해도) 삭제한다
- OCR 텍스트는 바로 마스킹하고 저장·로그하지 않는다. 마스킹이 실패하면 처리를 멈춘다(LLM에도 보내지 않음)
- 서류 처리 동의(DOC_PROCESS) 확인과 `user_benefits.doc_checks` 저장은 백엔드가 한다
- 테스트에는 **가짜 서류만** 쓴다. 서류 이미지는 커밋하지 않는다(.gitignore)

## 실행 (Windows, Python 3.11)

```
cd ocr-server
python -m venv .venv
.venv\Scripts\activate
python -m pip install paddlepaddle -i https://www.paddlepaddle.org.cn/packages/stable/cpu/
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8001
```

처음 `/check`를 부를 때 한국어 모델을 내려받아서 시간이 걸린다.

## API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | /health | 서버 확인 |
| POST | /check | 서류 검토 (multipart: `file`, `expected_type`, `max_age_days`) |
| POST | /text | 마스킹된 텍스트 줄 (EASY-02 고지서 사진용) |

`expected_type`: `RESIDENT_REGISTER`(주민등록등본) · `HEALTH_INSURANCE_PAYMENT`(건강보험료 납부확인서) · `ENROLLMENT`(재학증명서) · `INCOME_AMOUNT`(소득금액증명원) (D-18)

`/check` 응답

```json
{
  "result": "PASS",
  "detectedType": "RESIDENT_REGISTER",
  "issueDate": "2026-11-10",
  "foundFields": ["세대주", "주소", "세대원"],
  "missingFields": [],
  "reason": "서류가 확인됐어요."
}
```

| result | 뜻 | 화면 |
| --- | --- | --- |
| PASS | 종류·필수 항목·발급일 모두 충족 | 충족 |
| MISSING | 필수 항목을 못 찾음 | 부족 → ② 발급 안내 |
| INVALID | 발급일 없음·기한 지남·미래 날짜 | 요건 미달 → ② 발급 안내 |
| TYPE_MISMATCH | 기대한 서류와 다름 | 종류 불일치 안내 |
| UNREADABLE | 흐림·글자 부족·마스킹 실패 | 재촬영 요청 |

에러: 413 `FILE_TOO_LARGE`(10MB 초과), 415 `UNSUPPORTED_FORMAT`(JPG·PNG·PDF 외), 400 `UNKNOWN_DOC_TYPE`

## 마스킹

| 대상 | 결과 |
| --- | --- |
| 주민등록번호·외국인등록번호 | `900101-1******` |
| 휴대폰·전화 | `010-****-5678` |
| 계좌번호 | `***-***-**6789` |

## 테스트

```
pytest
```

마스킹·서류 판정 규칙만 시험한다(PaddleOCR 없이 돈다).
