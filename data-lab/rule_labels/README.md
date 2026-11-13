# 판정 조건 정답 라벨 (B)

LLM 조건 추출(DATA-03)의 정확도와 최소 신뢰도 기준선(D-61)을 정하기 위한 사람 라벨.
표본은 C의 정답셋과 같은 30건(`data-lab/gold_set/samples.csv`)이다.

## 파일

| 파일 | 내용 |
| --- | --- |
| `rule_labels.csv` | 사람이 채우는 정답. **한 줄 = 조건 하나** |
| `check_labels.py` | 형식 검사 + 근거 문구·숫자가 원문에 있는지 검사 (검증 모듈 재사용) |
| `test_check_labels.py` | 단위 테스트 |

```bash
python data-lab/rule_labels/check_labels.py      # 라벨을 쓰는 중간중간 실행. 오류 0이어야 완료
python -m unittest discover -s data-lab/rule_labels
```

## 칸

| 칸 | 값 | 규칙 |
| --- | --- | --- |
| service_id | 표본의 서비스ID | |
| group | `0`, `1`, `2` ... | 조건 묶음 (D-62). 0 = 모든 경우에 공통인 조건. 1 이상 = "또는"으로 나뉘는 묶음. "또는"이 없으면 전부 0 |
| field | 아래 필드 목록 | |
| operator | `EQ` / `IN` / `LTE` / `GTE` / `BETWEEN` | |
| values | `;`로 구분 | `BETWEEN`은 `작은값;큰값`. 코드·영문 값은 아래 목록 그대로 |
| source_quote | 원문 그대로 복사 | **한 필드 안의 연속된 문구**. 여러 필드에 걸치면 조건을 나눠 적는다 |
| question | ANSWER만 | 사용자에게 물을 예/아니오 질문 ("벼를 0.1ha 이상 재배하나요?") |
| labeler / memo | 이름 / 자유 | 애매한 판단, 지원조건 코드와 다른 점 |

## 필드 목록

| field | values | 예 |
| --- | --- | --- |
| AGE | 만 나이 정수 | 만 19~34세 → `BETWEEN 19;34`, 35세 미만 → `LTE 34`, 65세 이상 → `GTE 65` |
| GENDER | `M` / `F` | 여성 → `EQ F` |
| INCOME_PCT | 기준 중위소득 % | 중위소득 125% 이하 → `LTE 125` |
| HOUSEHOLD_SIZE | 정수 | |
| HOUSEHOLD_TYPE | `JA0403` 한부모·조손, `JA0404` 1인가구, `JA0411` 다자녀, `JA0413` 신규전입, `JA0414` 확대가족 | 한부모가족 → `IN JA0403` |
| STATUS | `JA0313`~`JA0316` 농·어·축산·임업, `JA0317`~`JA0320` 초·중·고·대학생, `JA0326` 근로자, `JA0327` 구직자, `SELF_EMPLOYED` | 대학생 → `IN JA0320` |
| SENSITIVE | `JA0301`~`JA0303` 예비부모·난임/임산부/출산·입양, `JA0328` 장애, `JA0329` 보훈, `JA0330` 질병, `JA0401` 다문화, `JA0402` 북한이탈 | 등록 장애인 → `IN JA0328` |
| HOUSING | `OWN` / `JEONSE` / `MONTHLY` / `ETC` | 월세 거주 → `IN MONTHLY` |
| HAS_HOUSE | `true` / `false` | 무주택자, 주택 소유자 제외 → `EQ false` |
| MARITAL | `SINGLE` / `MARRIED` / `ETC` | |
| CHILDREN_COUNT | 정수 | 자녀 3명 이상 → `GTE 3` |
| YOUNGEST_CHILD_AGE | 만 나이 정수 | |
| WELFARE | `BASIC` 기초생활수급 / `NEAR_POOR` 차상위 | 수급자·차상위 → `IN BASIC;NEAR_POOR` |
| REGION | 시군구·시도 이름 | 기관코드 표(D-56)보다 **좁을 때만** (D-65) |
| ANSWER:{key} | `Y` / `N` (통과하는 답) | 프로필에 없는 조건 (D-63). key는 영문 소문자_밑줄. question 칸 필수 |
| UNPARSED | 비움 | 사용자가 스스로 답하기 어려운 모호한 기준 ("저소득층", "의사 판단하에 필요한 자"). 이 줄이 있으면 그 공고는 최대 조건부 (D-64) |
| NONE | 비움 | 조건이 하나도 없는 공고 ("지역 주민 모두"). 라벨을 빠뜨린 것과 구분하려고 적는다 |

## 판단 기준

1. **원문만 본다.** 지원조건 코드(JA)는 보지 않는다. 코드와 다르다고 느끼면 memo에 적는다 (D-65)
2. **지원대상·선정기준만 조건이다.** 우선선발·예산 범위 내·선착순은 적지 않는다. 중복수혜 불가는 C 라벨(DATA-06)이라 적지 않는다
3. **제외 대상도 조건이다.** "주택 소유자 제외" → `HAS_HOUSE EQ false`. 프로필에 없는 제외 조건은 ANSWER로 적고 values = `N` ("공공임대주택에 살고 있나요?" → N이어야 통과)
4. **"또는"이 필드를 넘나들면 group을 나눈다.** 같은 필드 안의 "또는"은 IN 하나로 적는다
5. **미만·초과는 정수 경계로 바꾼다.** 나이 35세 미만 → `LTE 34`. 소득 %의 미만·초과는 그대로 적고 memo에 "미만"을 남긴다
6. **애매하면 UNPARSED.** 추측해서 필드를 고르지 않는다. 모든 공고는 최소 한 줄이 있어야 한다 (조건이 없으면 NONE)
7. **지역**: 소관기관 지역과 같으면("도 내 주소지를 둔") 적지 않는다. 기관보다 좁은 지역("서귀포시 동부/서부"), 거주 기간("1년 이상 도에 주소") 처럼 지역 코드로 안 되는 것은 ANSWER

## 예시

`449000000119` 천안시민 인플루엔자 예방접종: "60~64세 주민 / 14세~59세 기초생활수급자·장애의 정도가 심한 장애인 / 14세~59세 국가유공자"

| group | field | operator | values | source_quote | question |
| --- | --- | --- | --- | --- | --- |
| 1 | AGE | BETWEEN | 60;64 | 60~64세 주민 | |
| 2 | AGE | BETWEEN | 14;59 | 14세~59세 기초생활수급자 · 장애의 정도가 심한 장애인 | |
| 2 | WELFARE | IN | BASIC | 14세~59세 기초생활수급자 | |
| 3 | AGE | BETWEEN | 14;59 | 14세~59세 기초생활수급자 · 장애의 정도가 심한 장애인 | |
| 3 | ANSWER:severe_disability | EQ | Y | 장애의 정도가 심한 장애인 | 장애의 정도가 심한 장애인인가요? |
| 4 | AGE | BETWEEN | 14;59 | 14세~59세 국가유공자 | |
| 4 | SENSITIVE | IN | JA0329 | 14세~59세 국가유공자 | |

- 수급자와 중증장애인은 필드가 달라서(WELFARE / ANSWER) 묶음을 나눴다. 같은 필드였다면 IN 하나로 적는다
- "중증" 여부는 SENSITIVE 코드(JA0328 장애)로는 구분이 안 되므로 ANSWER

`161300000099` 청년월세 지원 (일부)

| group | field | operator | values | source_quote | question |
| --- | --- | --- | --- | --- | --- |
| 0 | AGE | BETWEEN | 19;34 | 만 19~34세 이하 | |
| 0 | ANSWER:live_apart_from_parents | EQ | Y | 부모님과 별도 거주하는 | 부모님과 따로 살고 있나요? |
| 0 | HAS_HOUSE | EQ | false | 무주택자 | |
| 0 | ANSWER:public_rental | EQ | N | 공공주택특별법에 따른 공공임대주택 거주 | 공공임대주택에 살고 있나요? |

- memo: "나이는 출생연도 기준(만 19~34세가 되는 해)이라 생일 전후로 엔진 판정과 다를 수 있음"
