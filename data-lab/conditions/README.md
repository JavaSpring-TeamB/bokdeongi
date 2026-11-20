# DATA-03 판정 조건 추출 (LLM) — 프롬프트와 출력 형식

공고 원문에서 "누가 받을 수 있는지" 조건을 뽑아 `benefit_rules` 줄 모양으로 바꾸는 프롬프트다.
백엔드에 붙일 때(B) 이 폴더의 파일을 그대로 쓴다. 측정·수정은 C가 aitest에서 하고 여기로 옮긴다.

| 파일 | 내용 |
| --- | --- |
| `prompt_c3_system.txt` | system 메시지 (여러 공고를 한 번에 묻는 batch 판, aitest `ConditionPrompt.batchSystem()`과 같은 글) |
| `prompt_c3_user.txt` | user 메시지 모양 (공고마다 블록 반복) |
| `output_format.json` | 응답 JSON 모양과 예시 |

## 버전

| 버전 | 바뀐 것 | 측정 |
| --- | --- | --- |
| c1 | 처음 판 | 15건 F1 0.64 |
| c2 | 코드 필드(HOUSEHOLD_TYPE·STATUS·SENSITIVE·HOUSING·WELFARE)는 값 하나여도 IN, 같은 필드 열거는 IN 하나, 미취업·중증 장애는 ANSWER | 30건 F1 0.95 (3.8 Flash LOW) |
| c3 | c2 + B 검수 반영: 입학생·입학 예정자는 ANSWER / 목록 뒤 범주 이름("군인 및 농부 등 고위험군")은 ANSWER 하나, UNPARSED 없음, 필드 있는 항목은 묶음 따로 (D-98) / 농가·어가·축산(양봉·토봉)·임업은 ANSWER가 있어도 STATUS 같이 / 이름 있는 중복지원 목록은 ANSWER 한 줄 [N] / 단체·시설은 개인 질문 아님 / 서류 이름은 조건 아님, 서비스명·지원내용에서 대상 찾기 / 질병 JA0330은 병명 ANSWER 같이 | 아직 측정 안 함 |

## 호출 설정 (D-97)

- 모델 `gemini-3.8-flash`, 생각 수준 `LOW`, temperature는 지정하지 않음(기본 1.0)
- 3건씩 묶어서 한 번에 묻는다 (지시문을 나눠 써서 비용이 줄어듦)
- 응답은 `output_format.json` 모양. Spring AI는 `.call().entity(ConditionExtractionBatch.class)` 또는 `responseEntity(...)`로 받음
- 전체 일괄은 Batch API(반값)

## 출력 → benefit_rules

| 응답 칸 | benefit_rules | 비고 |
| --- | --- | --- |
| `serviceId` | (benefits.service_id로 benefit_id 찾기) | 묶음 응답을 공고와 맞추는 키 |
| `group` | `rule_group` | 0 = 공통, 1 이상 = "또는" 묶음 (D-62) |
| `field` | `field` | 판정 필드 16개 + `ANSWER:{key}`, `UNPARSED`, `NONE`, `OUT_OF_SCOPE` |
| `operator` | `operator` | EQ / IN / LTE / GTE / BETWEEN. UNPARSED·NONE·OUT_OF_SCOPE는 빈 값 |
| `values` | `value` (JSON 배열) | 숫자도 문자열로 옴 ("19") → 필드에 맞게 변환 |
| `sourceQuote` | `source_quote` | |
| `question` | (ANSWER 질문 문구) | ANSWER일 때만 |
| — | `source` = `LLM`, `verified` = false | |

- `NONE`(조건 없음)·`OUT_OF_SCOPE`(시설·단체 대상)는 그 공고의 유일한 줄로 온다
- `UNPARSED`가 있으면 그 공고는 최대 조건부 (D-64, `benefits.unparsed_conditions`)

## 저장 전 검증 (버리는 기준)

1. **근거 문구는 그 공고의 원문에만 대조한다.** 3건 묶음이어도 다른 공고 원문에서 찾아지면 안 된다.
   aitest 채점도 공고 ID별 원문(서비스명·지원대상·선정기준·지원내용·신청방법)으로만 확인한다.
   서비스명(제목)도 넣는다. 대상이 제목에만 있는 공고가 있다 (예: "○○시설 수용자")
   원문에 글자 그대로 없으면 그 줄을 버린다 (D-59, `ExtractionValidator.quoteFound`)
2. 값의 숫자는 근거 문구 안 숫자에서 나와야 한다 (`ExtractionValidator` RULE)
3. `field`가 목록에 없거나, 코드 값이 목록에 없으면 버린다
4. D-60: 공고당 2회 호출해 결과가 다르면 조건부로 둔다

## 측정 결과 (정답셋 30건, c2, 3건 묶음, 2026-11-19~20)

| 모델 | F1 | 재현율/정밀도 | 정답과 완전히 같은 공고 | 근거 지어낸 것 | 30건 토큰(입력/출력) |
| --- | --- | --- | --- | --- | --- |
| 3.8 Flash (LOW) | 0.95 | 0.97/0.92 | 23/30 | 0 | 38,640 / 9,121 |
| 3.1 Flash-Lite | 0.83 | 0.82/0.84 | 16/30 | 0 | 38,640 / 9,109 |
| 2.5 Flash-Lite | — | — | — | — | 404 (모델 없음) |

- 외부기준 라벨(D-89, 예: HPV "저소득층")과 겹치는 예측은 LLM 점수에서 빼고 따로 센다
- 3.8 LOW의 생각 토큰은 집계에 빠졌을 수 있어 결제 콘솔 금액으로 다시 확인한다
