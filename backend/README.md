# backend

백엔드 담당이 Spring Boot 프로젝트를 생성하고, 패키지 구조·레이어 규칙을 이 문서에 정리한다.

## 프로젝트 생성 설정 (Spring Initializr)

| 항목 | 값 |
| --- | --- |
| Project | Gradle - Groovy |
| Language | Java |
| Spring Boot | 4.1.x (최신 패치) |
| Java | 21 |
| Packaging | Jar |
| 생성 위치 | 이 `backend/` 폴더 (gradlew가 `backend/gradlew`에 오도록) |

의존성 후보 (필요한 것만 추가하고 PR에 이유를 적는다)

- Spring Web, Validation
- Spring Security, OAuth2 Client (카카오 로그인)
- Spring Data JPA, PostgreSQL Driver, Flyway 또는 Liquibase(마이그레이션 도구는 DB 담당과 협의)
- Spring Batch (일일 동기화)
- Spring AI — Google GenAI(Gemini) 2.0.x
- Spring Boot Actuator (헬스체크)

`backend/gradlew`가 생기면 CI가 자동으로 빌드·테스트를 돌린다.

## 패키지 구조

<!-- 백엔드 담당 작성 -->

## 레이어·네이밍 규칙

<!-- 백엔드 담당 작성 -->
