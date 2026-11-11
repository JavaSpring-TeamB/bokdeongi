# API 실측 (B)

공공서비스(혜택) API 3종(serviceList, serviceDetail, supportConditions)을 한 번 전부 받아
필드 채움률, JA 코드 분포, 신청기한 형식, 구비서류 형식, 수정일시를 보고서로 만든다.
결과는 DATA-02(코드 → 규칙), DATA-04(날짜 파싱), PROF-01(프로필 항목) 확정에 쓴다.

외부 라이브러리 없이 Python 3.10 이상 표준 라이브러리만 쓴다.

## 실행 (레포 루트에서)

```bash
# 1. .env에 DATA_GO_KR_SERVICE_KEY=(공공데이터포털 Decoding 키) 입력

# 2. 수집 — 원본은 data-lab/data/raw/gov24/에 저장 (커밋 안 됨)
python data-lab/api_survey/collect.py

# 3. 보고서 생성 — data-lab/reports/api_survey.md
python data-lab/api_survey/analyze.py

# 테스트
python -m unittest discover -s data-lab/api_survey
```

- 중간에 끊기면 다시 실행한다. 이미 받은 페이지는 건너뛴다
- `--max-calls`(기본 300)로 한 번 실행에 쓰는 호출 수를 제한한다. 개발 계정 한도는 10,000
- 인증 오류가 나면 활용신청 직후라 키 등록이 아직 안 된 경우가 많다. 잠시 후 다시 실행한다
- 인증키는 로그에 마스킹해서만 출력한다
