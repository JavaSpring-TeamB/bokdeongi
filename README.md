# 복덩이

내 정보를 한 번 저장하면 받을 수 있는 공공 복지 혜택을 모아 보여주고,
AI가 신청 완료까지 단계별로 안내하는 개인화 복지 비서.

- 1차 데모 개발: 2026-11-11 ~ 2026-12-04
- 팀: 백엔드 · 데이터/규칙 로직 · AI 튜닝 · 프론트엔드 (4인)

## 구조

```
backend/      Spring Boot 4.1, Java 21, Gradle(Groovy)
frontend/     HTML · CSS · JavaScript
ocr-server/   로컬 OCR (PaddleOCR)
data-lab/     API 실측 · 정답셋 · 정확도 측정
docs/         설계 문서 링크
```

## 로컬 실행

```bash
cp .env.example .env            # 값 채우기 (커밋 금지)
docker compose up -d db         # MySQL 9.7 (로컬에 MySQL을 설치해 HeidiSQL로 쓰면 생략)
cd backend && ./gradlew bootRun
```

프론트는 `frontend/index.html`을 브라우저나 로컬 정적 서버로 연다.

## 작업 규칙

[AGENTS.md](AGENTS.md)를 먼저 읽는다. 브랜치 전략, PR 규칙, 반드시 지킬 원칙이 정리돼 있다.
