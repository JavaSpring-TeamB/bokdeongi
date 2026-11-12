# 정답셋 (C)

AI 추출(DATA-04 날짜, DATA-05 금액, DATA-06 중복 불가, DATA-07 서류, DATA-09 해시태그)의 정확도를 재기 위한 사람 라벨 30건.
프롬프트를 바꿀 때마다 이 정답과 비교해 점수를 낸다.

## 파일

| 파일 | 내용 | 커밋 |
| --- | --- | --- |
| `sample.py` | 원본에서 표본 30건 추출 | O |
| `samples.csv` | 표본 공고 원문 (공개 공고 데이터만) | O |
| `labels.csv` | 사람이 채우는 정답 | O |
| `hashtags_seed.csv` | 해시태그 사전 초안 40개 (hashtags 테이블 초기 데이터, D-44 변경) | O |

## 실행 (레포 루트에서)

```bash
# 원본이 없으면 먼저 수집 (api_survey/README.md 참고)
python data-lab/api_survey/collect.py

python data-lab/gold_set/sample.py          # samples.csv, labels.csv 생성
python -m unittest discover -s data-lab/gold_set
```

- 신청기한 분류(api_survey 보고서 3장)별 개수를 정해서 뽑는다: 날짜(연도 포함) 6, 상시 5, 매년 반복 4, 기관별 상이 3, 나머지 분류 각 2
- 같은 분류 안에서는 지원유형이 겹치지 않게 고른다. 개인·가구 대상만 뽑는다
- 시연 공고(서비스명에 '청년', '월세')는 항상 넣는다
- seed가 같으면 항상 같은 30건이 나온다. `labels.csv`는 이미 있으면 덮어쓰지 않는다

## 라벨링 가이드 (`labels.csv`)

`samples.csv`의 원문만 보고 채운다. 원문에 없는 내용은 추측하지 말고 비워 둔다.

| 칸 | 값 | 규칙 |
| --- | --- | --- |
| apply_start / apply_end / result_date | `2026-03-02` 또는 `2026-03` | 원문에 연도가 없으면 비움. 시작일만 있으면 start만. 일이 없고 연·월만 있으면 `2026-03`처럼 월까지만 적는다(첫날·말일로 채우지 않음) |
| date_status | `PARSED` / `ALWAYS` / `CHECK_NOTICE` | 날짜를 하나라도 적었으면 PARSED(월까지만 있어도 PARSED + MONTH). 상시·연중·수시는 ALWAYS. 기관별 상이·공고 참조·상대 기간·연도 없는 날짜는 CHECK_NOTICE |
| date_precision | `DAY` / `MONTH` / 비움 | 적은 날짜가 일까지 있으면 DAY, 월까지만 있으면 MONTH("4월 중", "7월 말" 포함). 화면에는 "2026년 4월~11월 (정확한 날짜는 공고 확인)"처럼 보여 준다. 날짜를 안 적었으면 비움 |
| recurring_yearly | `Y` / 비움 | "매년", "해마다", "연초"처럼 해마다 반복되면 Y |
| date_source_quote | 원문 그대로 | 날짜 판단의 근거가 된 부분을 복사 |
| max_amount | 숫자(원) | "최대 20만 원" → `200000`. 현물·서비스는 비움 |
| amount_period | `MONTH` / `YEAR` / `ONCE` | 월 단위 / 연 단위 / 1회 |
| annual_amount | 숫자(원) | 연간 환산. 월 20만 원 × 12개월 → `2400000`. 기간을 모르면 비움 |
| amount_source_quote | 원문 그대로 | 금액 근거 부분 |
| exclusion_text | 원문 그대로 | "다른 OO 지원과 중복 불가" 같은 문구. 없으면 비움 |
| self_docs | `주민등록등본;임대차계약서` | 신청자가 직접 내는 서류만, `;`로 구분. 공무원 확인 서류는 뺀다 |
| hashtags | `월세;청년;현금지원` | `hashtags_seed.csv`의 name만 1~5개, `;`로 구분 |
| labeler / memo | 이름 / 자유 | 애매한 점은 memo에 적고 팀에서 맞춘다 |

## 확인 필요 (PM)

- 신청기한 실측에서 "신청 불필요"(2.1%)와 "기관별 상이"(9.4%)가 꽤 많다. 지금 date_status 3값(PARSED/ALWAYS/CHECK_NOTICE)에서는 각각 ALWAYS·CHECK_NOTICE로 라벨하되 memo에 원래 분류를 남긴다. 화면에 따로 보여줄지 정해지면 값을 늘린다
