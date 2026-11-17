# 서류 목록 (document_types 시드, DATA-07)

서류 준비(ASST-04)·서류 검토(ASST-03)·서류 유효기간(ASST-07)에서 쓰는 `document_types` 테이블 데이터를 만든다.

## 파일

| 파일 | 내용 |
| --- | --- |
| `document_types.csv` | 서류 목록 원본. 고칠 때는 이 파일만 고친다 (VS Code로 열고, 엑셀·한셀로 저장하지 않는다) |
| `build_document_types.py` | CSV를 검사하고 SQL을 만든다 |
| `document_types.sql` | 공용 DB에 넣는 SQL. 자동 생성이므로 직접 고치지 않는다 |

## 실행

```bash
python data-lab/documents/build_document_types.py
python -m unittest discover -s data-lab/documents
```

공용 DB에는 D가 `document_types.sql`을 실행한다. 여러 번 실행해도 결과가 같다.
`name`이 같은 줄은 새 값으로 덮어쓰고 `id`는 그대로라서, `benefits.documents`·`users.doc_records`가 가리키는 id가 바뀌지 않는다.

## 고른 기준

- 공공서비스 API 원본 중 개인·가구 대상 공고 9,934건의 `구비서류` 칸에서 자주 나오는 **발급받거나 가지고 있는 서류** 29종
- 신청서·동의서·사업계획서처럼 공고마다 서식이 다른 것은 넣지 않는다 (공고 원문 이름 그대로 보여 준다)
- `mentions`: 구비서류 칸에 그 서류 이름이나 별칭이 나온 공고 수 (SQL에는 넣지 않는 참고값)

## 칸 규칙

| 칸 | 값 |
| --- | --- |
| name | 화면에 보여 줄 이름. 50자 이내, 중복 불가 |
| aliases | `;`로 구분한 다른 이름. 공고에서 뽑은 서류 이름(DocumentRuleExtractor)을 이 목록에 맞춰 연결한다. 한 별칭이 두 서류에 들어가면 멈춘다 |
| issuer | 발급처 |
| online_available | Y / N |
| issue_url | 발급 기관 누리집 첫 화면 (정부24, 대법원 전자가족관계등록시스템, 국민건강보험공단, 홈택스, 위택스). 서류별 세부 메뉴 주소는 자주 바뀌어서 넣지 않았다. 학교·병원·회사처럼 정해진 곳이 없으면 비움 |
| issue_method | 발급 방법 한 줄 |
| default_validity_days | 공고에서 흔히 요구하는 유효기간(일). 구비서류 칸의 "발급일로부터 N개월 이내" 문구를 세어 가장 많은 값을 넣었다 (등본 3개월 29건·1개월 21건 → 90). 근거가 거의 없으면 비움. 실제 기한은 공고마다 다르므로 공고 원문에 기한이 있으면 그 값을 우선한다 |
| alternatives | 대신 낼 수 있는 서류 |
| ocr_supported | OCR 서버가 읽는 서류만 Y. D-18의 4종(주민등록등본, 건강보험료 납부확인서, 재학증명서, 소득금액증명원)이 아니면 멈춘다 |

## 확인이 필요한 것

- 재학·성적증명서는 학교마다 발급처가 달라 주소를 비워 두었다
- 유효기간은 근거가 적은 서류(혼인관계증명서 1건, 재직증명서 2건 등)는 비우거나 많이 쓰인 값만 넣었다
