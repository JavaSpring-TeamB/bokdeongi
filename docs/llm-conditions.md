# LLM 판정 조건 추출 (DATA-03)

공고 원문에서 "누가 받을 수 있는지" 조건을 LLM으로 뽑아 `benefit_rules`에 넣는다.
프롬프트와 출력 형식은 C가 측정한 `data-lab/conditions/`(c3)를 그대로 쓴다.

## 범위 (D-93)

- 실제 공고 전체는 지원조건 코드 + 지역으로만 판정한다 (D-09)
- LLM은 **정답셋 30건과 시연 페르소나가 볼 혜택에만** 미리 돌린다. 조회 시 실시간 생성은 하지 않는다
- 검수 라벨(MANUAL)이 있는 공고는 건너뛴다. 라벨이 우선이다
- 보내는 것은 공개 공고문뿐이다. 회원 정보는 보내지 않는다 (D-92)

## 돌리는 법 (B만)

1. `.env`에 Gemini 키와 대상 서비스ID를 넣는다

   ```
   GEMINI_API_KEY=...
   AI_CHAT_MODEL=google-genai
   CONDITIONS_SERVICE_IDS=161300000099,449000000119
   ```

2. 서버를 띄우면 시작 직후 한 번 돈다. 로그에 `조건 추출(DATA-03, c3) 결과: Report[...]`가 찍힌다
3. **돌린 뒤 `CONDITIONS_SERVICE_IDS`를 비운다.** 그대로 두면 서버를 띄울 때마다 다시 부른다

호출 수는 `2 × ⌈공고 수 / 3⌉`이다 (3건씩 묶어 같은 요청을 두 번, D-60·D-97). 30건이면 20번.

## 저장 규칙

| 단계 | 내용 |
| --- | --- |
| 근거 문구 | 그 공고의 서비스명·지원대상·선정기준·지원내용·신청방법에 글자 그대로 있어야 한다. 묶음의 다른 공고 원문은 보지 않는다 (D-59) |
| 숫자 | 값의 숫자가 근거 문구 안에 있어야 한다. 나이 미만·초과의 정수 경계는 허용 |
| 필드·값 | 프롬프트 [필드] 목록에 있어야 한다. 코드 필드는 IN, 대소문자·숫자 표기를 맞춘다 |
| 신뢰도 | 숫자 0.4 + 코드 모순 없음 0.3 + 두 번 일치 0.3. 확인 못 한 항목은 절반 (D-61) |
| 버린 줄 | 같은 묶음에 UNPARSED로 남긴다. 줄을 빼면 조건이 느슨해져 해당 없는 사람이 '해당'이 될 수 있어서다 |
| 두 번째에만 있는 줄 | 첫 결과가 조건을 빠뜨렸을 수 있으니 0번 묶음 UNPARSED (공고 전체 최대 조건부) |
| 저장 | 그 공고의 규칙을 LLM 규칙으로 통째로 바꾼다 (코드 규칙도 지움, 원문 우선 — 검수 라벨과 같은 방식, D-99). `source = LLM`, `verified = false`, `source_modified_at` = 공고 수정일시 |
| 그사이 원문 변경 | 저장 직전 공고 수정일시가 다르면 저장하지 않는다. 저장 뒤 원문이 바뀌면 동기화가 코드 규칙을 다시 넣고 LLM 규칙을 지운다 |

엔진은 신뢰도 0.80 미만 LLM 규칙을 조건부로 본다. 질문(ANSWER)·지역·수급처럼 숫자도 코드도 없는 줄은
두 번 일치해도 0.65라서, 검수하기 전에는 조건부로 남는다.

## 검수 → 수동 규칙

1. 실행하면 `backend/conditions-review/conditions-review-c3-{시각}.csv`가 생긴다 (커밋하지 않음)
   - 앞 칸은 `rule_labels.csv`와 같고 `labeled_on`(원문 수정일, 한국 날짜)이 더 있다
   - 뒤 칸 `status`·`confidence`·`checks`·`reason`은 검수 참고용 (`KEPT` / `LOW_CONFIDENCE` / `DROPPED` / `UNPARSED` / `SPECIAL` / `REPEAT_ONLY`)
2. 라벨 기준(`data-lab/rule_labels/README.md`)으로 줄을 고치고, 맞는 줄만 `data-lab/conditions/reviewed_rules.csv`에 붙인다.
   `labeler`를 채운다. 엑셀로 저장하지 않는다
3. `python data-lab/rule_labels/export_label_rules.py` → `label-rules.csv`에 함께 나간다
4. 다음 동기화부터 그 공고는 수동 규칙(MANUAL)으로 바뀌고 질문 문구도 검수한 것을 쓴다.
   원문이 바뀌면(수정일이 `labeled_on`과 다르면) 넣지 않는다 (D-93)

정답셋 30건과 같은 서비스가 검수 파일에 있으면 내보내기가 멈춘다.

## 프롬프트를 바꿀 때

C가 `data-lab/conditions/`에서 측정한 뒤, system 글을 `backend/src/main/resources/prompts/`에 복사하고
`ConditionPrompt.VERSION`을 올린다. 두 파일이 다르면 `ConditionPromptTest`가 실패한다.
