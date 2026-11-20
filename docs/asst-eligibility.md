# 신청 도우미 ① 자격 확인 (ASST-02)

판정은 규칙 엔진(ELIG-01) 결과 그대로다. 이 단계는 조건별로 **충족 / 확인 필요 / 미충족**과 근거 원문을 보여 주고,
부족한 정보 중 예/아니오로 답할 수 있는 것만 묻는다.

## 연결 (A)

신청 건(ASST-01)과 진행 단계 저장은 A가 맡는다. B는 서비스만 만든다.

```java
// POST /api/applications/{id}/eligibility
Optional<EligibilityCheckResponse> r = eligibilityCheckService.check(userId, application.getBenefitId());
// 비어 있으면: 혜택이 없거나 종료됐거나 개인 대상이 아님 → 404 또는 '시작 불가' 안내 (ASST-01 예외와 같음)
```

- 부를 때마다 그 혜택만 다시 판정한다 (프로필·답이 바뀌었을 수 있어서)
- `next.canProceed = false`면 ② 서류 준비로 넘기지 않는다
- `next.manualConfirmRequired = true`면 `next.sourceText`(지원대상·선정기준 원문)를 보여 주고 '직접 확인했어요'를 받아야 진행한다.
  체크 여부는 신청 건 진행 상태에 저장한다 (A)
- `next.agencyCheckRecommended = true`면 기관 문의를 권하되 진행은 허용한다

## 추가 질문

`questions`의 `key`로 기존 답 저장 API를 부른다. 답하면 그 혜택이 바로 다시 판정되므로, 화면은 자격 확인을 다시 부르면 된다.

```
PUT /api/me/answers
{"benefitId": 123, "answers": {"married_within_7y": "Y"}}     // Y / N / UNSURE
```

미충족이면 질문에 답해도 결과가 바뀌지 않으므로 `questions`는 비어 있다.

## 응답 예

```json
{
  "benefitId": 123, "title": "신혼부부 전세자금 대출", "result": "CONDITIONAL", "summary": "확인할 조건이 1개 있어요",
  "groups": [
    {"group": 0, "alternative": false, "title": "공통 조건", "status": "CHECK", "conditions": [
      {"field": "AGE", "label": "나이", "status": "MET", "expected": "만 19~39세", "actual": "만 35세",
       "sourceQuote": "만 19세 이상 39세 이하", "checkType": null, "guide": null},
      {"field": "ANSWER:married_within_7y", "label": "추가 질문", "status": "CHECK", "expected": "Y", "actual": "미입력",
       "sourceQuote": "혼인 7년 이내", "checkType": "QUESTION", "guide": "혼인한 지 7년 이내인가요?"}]},
    {"group": 1, "alternative": true, "title": "다음 중 하나 (1)", "status": "MET", "conditions": ["..."]}
  ],
  "questions": [{"key": "married_within_7y", "text": "혼인한 지 7년 이내인가요?", "sourceQuote": "혼인 7년 이내"}],
  "next": {"canProceed": true, "manualConfirmRequired": false, "agencyCheckRecommended": false,
           "message": "아래 질문에 답하면 바로 다시 확인해요", "sourceText": null, "nearMiss": null, "similar": []}
}
```

| 칸 | 값 |
| --- | --- |
| `status` (조건·묶음) | `MET` 충족 / `CHECK` 확인 필요 / `NOT_MET` 미충족. 묶음은 미충족 > 확인 필요 > 충족 |
| `checkType` | `QUESTION` 예/아니오 질문, `PROFILE` 프로필 미입력, `SENSITIVE` 민감정보 동의 필요, `AGENCY` 기준선 근처·'아직 몰라요', `SOURCE` 공고 원문 확인 |
| `next.nearMiss` | 미충족일 때 첫 미충족 조건과 아깝게 탈락 이유 ("2027년 3월 2일부터 나이 조건을 충족해요") |
| `next.similar` | 미충족일 때 같은 서비스 분야에서 지금 '해당'인 혜택, 마감 빠른 순 3건 |

`message` 우선순위: 미충족 → 직접 확인 필요 → 추가 질문 → 프로필 입력 → 기관 문의 → 해당 → 그 밖의 확인 필요.
