# backend

백엔드 담당이 Spring Boot 프로젝트를 생성하고, 패키지 구조·레이어 규칙을 이 문서에 정리한다.

## 프로젝트 생성 설정 (Spring Initializr)

| 항목 | 값 |
| --- | --- |
| Project | Gradle - Groovy |
| Language | Java |
| Spring Boot | 4.1.2 |
| Java | 21 |
| Packaging | Jar |
| 생성 위치 | backend/ (Gradle Wrapper: backend/gradlew) |

Spring Boot 프로젝트 및 Gradle Wrapper 구성을 완료하였다.
CI에서는 backend/gradlew를 사용하여 빌드 및 테스트를 수행할 예정이다.

### 의존성

프로젝트 초기 생성 시 필요한 의존성을 추가하였으며, 이후 기능 구현에 필요한 의존성은 해당 기능 개발 시 추가한다.

#### 초기 추가 의존성

| 의존성 | 용도 |
|---|---|
| Spring Web MVC | REST API 구현 및 HTTP 요청 처리 |
| Validation | 요청 데이터 유효성 검증 |
| Spring Data JPA | 데이터베이스 접근 및 ORM |
| PostgreSQL Driver | PostgreSQL 데이터베이스 연결 |
| Spring Boot Actuator | 애플리케이션 상태 확인 및 모니터링 |

#### 추후 추가 예정 의존성

| 의존성 | 용도 |
|---|---|
| Spring Security | 인증 및 인가 처리 |
| OAuth2 Client | 카카오 소셜 로그인 |
| Flyway 또는 Liquibase | 데이터베이스 스키마 변경 이력 관리 |
| Spring Batch | 복지 데이터 일일 동기화 |
| Spring AI — Google GenAI | Gemini 기반 AI 기능 연동 |

#### 의존성 관리 규칙

- 실제 구현에 필요한 의존성만 추가한다.
- 새로운 의존성을 추가하거나 변경하는 경우 PR에 변경 이유를 작성한다.
- Flyway 또는 Liquibase는 DB 담당자와 협의 후 결정한다.
- Spring AI는 프로젝트에서 사용하는 Spring Boot 버전과 호환되는 버전을 확인한 후 추가한다.
- 사용하지 않는 의존성은 불필요하게 추가하지 않는다.

## 패키지 구조

기능별 패키지 구조를 사용하며, 각 기능 내부에서 Controller, Service, Repository 등의 레이어를 구분한다.

기본 패키지는 `com.bokdeongi.backend`로 설정한다.

아래 패키지 구조는 백엔드 개발을 위한 기본 설계이며 실제 기능 구현 시 필요한 패키지를 생성한다.

```text
com.bokdeongi.backend
├── BackendApplication.java
├── auth/                 # 회원가입, 로그인, 인증
├── profile/              # 사용자 프로필 관리
├── benefit/              # 복지 혜택 조회 및 검색
├── eligibility/          # 복지 혜택 자격 판정
├── assistant/            # AI 신청 도우미
├── application/          # 신청 현황 및 진행 단계
├── notification/         # 알림
└── global/               # 공통 설정 및 예외 처리
    ├── config/
    ├── exception/
    └── response/
```

각 기능 패키지는 필요한 레이어만 생성한다.

예시 (`auth/`):

```text
auth/
├── controller/
├── service/
├── repository/
├── entity/
└── dto/
    ├── request/
    └── response/
```

- 기능별로 코드를 분리하여 유지보수성을 높인다.
- 공통 기능은 `global/`에서 관리한다.
- 사용하지 않는 레이어 패키지는 미리 생성하지 않는다.
- 패키지 구조 변경이 필요한 경우 담당자 간 협의 후 반영한다.

## 레이어·네이밍 규칙

### 레이어 규칙

| 레이어 | 역할 |
|---|---|
| Controller | HTTP 요청 처리, 입력값 검증, 응답 반환 |
| Service | 비즈니스 로직 처리 및 트랜잭션 관리 |
| Repository | Spring Data JPA 기반 데이터베이스 접근 |
| Entity | 데이터베이스 테이블과 매핑되는 객체 |
| DTO | API 요청 및 응답 데이터 전달 |
| Global | 공통 설정, 예외 처리, 공통 응답 관리 |

- Controller에는 비즈니스 로직을 작성하지 않는다.
- Controller는 Service를 통해 비즈니스 로직을 호출한다.
- Service는 Repository를 통해 데이터에 접근한다.
- Entity를 API 요청 및 응답에 직접 노출하지 않고 DTO를 사용한다.
- 데이터 변경 작업에는 필요한 트랜잭션을 적용한다.
- 공통 예외 처리는 `global/exception`에서 관리한다.
- 기능별 비즈니스 로직은 해당 기능 패키지 내부에서 관리한다.

### 네이밍 규칙

| 구분 | 규칙 | 예시 |
|---|---|---|
| Package | 소문자 | `auth`, `benefit` |
| Class | PascalCase | `AuthService` |
| Method | camelCase | `findBenefits()` |
| Variable | camelCase | `userId` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| Controller | `*Controller` | `AuthController` |
| Service | `*Service` | `AuthService` |
| Repository | `*Repository` | `UserRepository` |
| Entity | 단수형 | `User` |
| Request DTO | `*Request` | `LoginRequest` |
| Response DTO | `*Response` | `LoginResponse` |

### API 및 코드 작성 규칙

- REST API는 HTTP 메서드(GET, POST, PUT, PATCH, DELETE)를 용도에 맞게 사용한다.
- API 경로와 요청·응답 형식은 노션 기능 명세를 기준으로 작성한다.
- 요청 데이터 검증에는 Jakarta Validation을 사용한다.
- 새로운 기능 구현 시 관련 테스트 코드를 함께 작성한다.
- 인증 및 인가 처리는 공통 보안 설정을 따른다.
- DB 비밀번호와 API 키 등 민감정보를 코드에 직접 작성하지 않는다.
- 기능 명세나 설계 결정이 불명확한 경우 임의로 구현하지 않고 팀과 협의한다.