# 공공서비스 API 동기화 (DATA-01)

공공데이터포털 `대한민국 공공서비스(혜택) 정보` API 3종(serviceList·serviceDetail·supportConditions)을 받아
`benefits`·`benefit_rules`를 채운다. 코드: `backend/src/main/java/com/bokdeongi/backend/sync/`.

## 동작

| 단계 | 내용 |
| --- | --- |
| 수집 | 세 API를 500건씩 끝까지 받는다. 하루 약 66~69회 호출(한도 10,000). 한 페이지가 실패하면 3번까지 다시 시도 |
| 비교 | 서비스ID별로 DB의 `api_modified_at`과 비교. 바뀐 혜택만 원문·가공값·코드 규칙을 다시 저장 |
| 가공 | 지원조건 코드 → 규칙·`target_scope`, 기관 → `region_code`, 신청기한·금액·중복 불가·구비서류·해시태그 (모두 규칙 기반, LLM 없음) |
| 상태 | 매번 다시 계산: 기간이 지나면 `EXPIRED`, 목록에서 사라지면 `ENDED` (지우지 않음) |
| 격리 | 상세·지원조건이 빠졌거나 형식이 깨진 레코드는 반영하지 않고 `sync_error`에 사유. 다음 동기화에서 정상이면 풀린다 |
| 변경 감지 | 원문이 바뀐 혜택은 신청 기간·대상·금액·내용·서류·신청 방법·이름을 비교해 `last_change_summary`("신청기한이 변경됐어요 (외 1건)")·`last_changed_fields`·`last_changed_at`에 남긴다 (DATA-08). 조회수 같은 사소한 칸은 무시 |
| 검수 라벨 | `backend/src/main/resources/eligibility/label-rules.csv`(검수한 30개 서비스)를 원문 수정일이 라벨과 같을 때만 수동 규칙(MANUAL)으로 넣는다. 라벨이 있으면 코드 규칙은 지운다(원문 우선). 원문이 바뀌면 라벨은 빠지고 코드 규칙으로 돌아간다 (D-93) |
| 판정 | 혜택이 바뀌었으면 회원 전체를 다시 판정 (`eligibility_results`) |
| 기록 | `sync_logs` 한 줄 (operation = SERVICE_LIST, 세 API 호출 수 합계, 새·갱신·종료·격리 건수) |

- 원문이 바뀐 혜택은 LLM·수동 규칙(`source` ≠ CODE) 중 옛 원문으로 만든 것을 지우고 `rules_status = PENDING`으로 돌린다 (DATA-03이 다시 만든다).
- 받은 목록이 기존 혜택의 절반도 안 되면 API 이상으로 보고 종료 처리를 건너뛴다 (`PARTIAL`).
- 한 API라도 끝까지 못 받으면 DB를 바꾸지 않고 `FAILED`. 서비스는 마지막 성공 데이터로 동작한다.

라벨 파일은 `data-lab/rule_labels/rule_labels.csv`를 고친 뒤 `python data-lab/rule_labels/export_label_rules.py`로 다시 만든다.
공용 DB에 `docs/db/v2.11_benefit_rules_field_length.sql`(benefit_rules.field 60자)이 적용돼 있어야 긴 질문 키 라벨이 들어간다.

## 설정 (.env)

| 키 | 기본값 | 설명 |
| --- | --- | --- |
| `DATA_GO_KR_SERVICE_KEY` | 없음 | 공공데이터포털 인증키 (Decoding 키) |
| `SYNC_ENABLED` | false | 매일 04:40(한국 시각) 자동 실행. **공용 DB에 쓰므로 배포 서버 한 곳에서만 켠다** |
| `SYNC_RUN_ON_STARTUP` | false | 서버가 뜬 직후 한 번 실행 (처음 적재·수동 실행용) |

둘 다 꺼져 있으면 서버를 띄워도 API를 부르지 않는다. 같은 시각에 두 서버가 돌려도 `sync_logs`의 RUNNING 기록으로 한쪽만 돈다.

## 처음 적재 (한 번)

1. 공용 DB에 `regions`, `document_types`(data-lab/documents/document_types.sql)가 들어 있는지 확인한다
2. 테스트 혜택을 지울 때만: `docs/db/clear_test_benefits.sql` (D가 실행)
3. `backend/.env`에 `SYNC_RUN_ON_STARTUP=true`를 넣고 서버를 띄운다 (`./gradlew bootRun`)
4. 로그에 `시작 시 동기화 결과: Report[status=SUCCESS ...]`가 나오면 `SYNC_RUN_ON_STARTUP`을 지운다

처음엔 약 1만 1천 건을 넣느라 몇 분 걸린다. 확인:

```sql
SELECT status, api_calls, fetched_count, new_count, updated_count, ended_count, quarantined_count, error_message
FROM sync_logs ORDER BY id DESC LIMIT 5;
SELECT status, COUNT(*) FROM benefits GROUP BY status;
```
