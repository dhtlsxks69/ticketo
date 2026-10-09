# Ticketo Project Configuration & Guidelines

## Technical Stack
- Language: Java 21
- Framework: Spring Boot 3.5.6
- Build Tool: Gradle (Kotlin DSL, build.gradle.kts)
- Database: MySQL 8.0 (utf8mb4)
- Persistence: MyBatis (JPA는 사용하지 않는다)
- Utilities: Lombok, Spring Validation

## Architecture & Package Structure
- Pattern: Feature-based Layered Architecture
- Root Package: `com.ticketo`

```text
com.ticketo
├── config              # 전역 설정
├── com                 # 공통 유틸/전역 컴포넌트
└── {feature}           # member, ticket, reservation
    ├── controller      # {Feature}Controller.java
    ├── service         # {Feature}Service.java
    ├── mapper          # {Feature}Mapper.java
    └── vo              # {Feature}VO.java
```

- MyBatis XML: `src/main/resources/mapper/{feature}/{Feature}Mapper.xml`
- 새 기능은 Member 도메인의 기존 코드 패턴을 따른다.

## Domain Rules
- 좌석은 좌석 번호가 아니라 수량 기반(total_quantity / remaining_quantity)으로 관리한다.
- 인증은 1차에서 이메일/비밀번호 확인(BCrypt)까지만. JWT는 마지막 단계에서 추가한다.
- 동시성 처리는 Reservation 기본 기능 완성 후 별도 단계에서 한다.
  (문제 재현 → 조건부 UPDATE → 비관적 락 순서)

## Working Rules
- 한 번에 한 도메인, 한 계층씩만 작업한다. 요청받지 않은 파일은 수정하지 않는다.
- 단계가 끝나면 앱 기동을 확인하고 커밋한다.
- 작업 종료 시 WORKLOG.md의 해당 날짜 항목에 실제 한 일과 커밋 해시를 추가한다.
- 커밋 메시지 예: `feat: Ticket 등록/조회 API 추가`