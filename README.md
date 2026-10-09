# Ticketo

공연 티켓 예매 서비스의 백엔드 API 프로젝트입니다. 회원, 공연(티켓), 예매 도메인으로 구성하며,
**잔여 수량 기반 좌석 관리**와 **예매 시 발생하는 동시성 문제의 재현과 해결 과정**을 정리하는 것이 목표입니다.

> 현재는 회원(가입/로그인)과 공연(CRUD) 도메인까지 구현되어 있습니다. 예매, 동시성 처리, JWT는 아직 구현 전입니다.

## 기술 스택

| 구분 | 내용 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.6 |
| Build | Gradle (Kotlin DSL) |
| Database | MySQL 8.0 (utf8mb4) |
| Persistence | MyBatis 3.0.5 starter (SQL은 XML 매퍼에 작성) |
| 비밀번호 해시 | BCrypt (`spring-security-crypto`만 사용, Spring Security 전체는 미사용) |
| 기타 | Lombok, Spring Validation |

## 구현 현황

- [x] 회원가입 (이메일 정규화, 중복 검사, BCrypt 해시 저장)
- [x] 로그인 (이메일/비밀번호 확인, 실패 시 401 통일 메시지)
- [x] 회원 목록 조회
- [x] 공연 등록 / 목록 / 상세 / 수정 / 삭제
- [x] 수량 기반 잔여 좌석 관리 (`total_quantity` / `remaining_quantity`)
- [ ] 회원 단건 조회 / 수정 (예정)
- [ ] 예매 / 예매 취소 / 내 예매 내역 (예정)
- [ ] 동시성 처리: 문제 재현 → 조건부 UPDATE → 비관적 락 비교 (예정)
- [ ] 공통 예외 처리, 응답 포맷 통일 (예정)
- [ ] JWT 인증 (예정)

## API 목록

컨트롤러에 실제로 구현된 API만 정리했습니다.

### Member

| 메서드 | 경로 | 설명 | 응답 |
|---|---|---|---|
| GET | `/api/members` | 회원 목록 조회 | 200 |
| POST | `/api/members` | 회원가입 | 201 / 409(이메일 중복) |
| POST | `/api/members/login` | 로그인 (토큰 발급 없음) | 200 / 401 |

- 비밀번호는 응답에 포함되지 않습니다.
- 로그인 실패는 이메일 없음/비밀번호 불일치 모두 같은 401 메시지로 응답합니다.

### Ticket

| 메서드 | 경로 | 설명 | 응답 |
|---|---|---|---|
| GET | `/api/tickets` | 공연 목록 (공연 일시 오름차순) | 200 |
| GET | `/api/tickets/{id}` | 공연 상세 (잔여 수량 포함) | 200 / 404 |
| POST | `/api/tickets` | 공연 등록 | 201 |
| PUT | `/api/tickets/{id}` | 공연 수정 | 200 / 404 / 409(판매된 수량보다 총 수량을 줄이는 경우) |
| DELETE | `/api/tickets/{id}` | 공연 삭제 | 204 / 404 |

- 잔여 수량은 서버가 관리하며, 등록 시 총 수량으로 초기화됩니다. 요청 값은 무시됩니다.

## 패키지 구조

```text
com.ticketo
├── config              # 전역 설정 (PasswordConfig 등)
├── com                 # 공통 컴포넌트
├── member
│   ├── controller      # MemberController
│   ├── service         # MemberService
│   ├── mapper          # MemberMapper
│   └── vo              # MemberVO, MemberLoginVO
└── ticket
    ├── controller      # TicketController
    ├── service         # TicketService
    ├── mapper          # TicketMapper
    └── vo              # TicketVO

src/main/resources
├── application.yml
├── schema.sql          # 기동 시 실행되는 테이블 생성 스크립트
└── mapper/{feature}/{Feature}Mapper.xml
```

`reservation` 패키지는 아직 없습니다. (`schema.sql`에는 `reservation` 테이블이 정의되어 있습니다.)

## 실행 방법

### 사전 준비

- JDK 21 (Gradle toolchain이 Java 21로 고정되어 있습니다)
- MySQL 8.0
- Gradle은 래퍼(`gradlew`, 9.6.0)를 사용하므로 별도 설치가 필요 없습니다.

### 1. 데이터베이스 생성

`application.yml`의 접속 URL이 데이터베이스를 자동으로 만들지 않으므로, 먼저 빈 데이터베이스를 만들어 주세요.

```sql
CREATE DATABASE ticketo DEFAULT CHARACTER SET utf8mb4;
```

### 2. 테이블 생성

별도로 실행할 필요가 없습니다. `spring.sql.init.mode=always` 설정에 따라 앱 기동 시 `schema.sql`이 실행되며,
`CREATE TABLE IF NOT EXISTS`를 사용하므로 반복 기동해도 안전합니다. (member, ticket, reservation 테이블)

### 3. 접속 정보 설정

`src/main/resources/application.yml`은 아래 환경변수로 DB 접속 정보를 주입받습니다.

| 환경변수 | 설명 |
|---|---|
| `DB_HOST` | DB 호스트 (미지정 시 `localhost`) |
| `DB_PORT` | DB 포트 (미지정 시 `3306`) |
| `DB_NAME` | 데이터베이스 이름 (미지정 시 `ticketo`) |
| `DB_USERNAME` | **필수, 직접 채워야 하는 항목** — DB 계정 |
| `DB_PASSWORD` | **필수, 직접 채워야 하는 항목** — DB 비밀번호 |

`DB_USERNAME`, `DB_PASSWORD`는 기본값이 없어서 지정하지 않으면 앱이 기동되지 않습니다.
본인의 환경에 맞는 값을 환경변수로 지정해서 사용하세요. 실제 비밀번호는 저장소에 커밋하지 마세요.

### 4. 실행

```bash
# macOS / Linux / Git Bash
DB_USERNAME=<계정> DB_PASSWORD=<비밀번호> ./gradlew bootRun
```

```powershell
# Windows PowerShell
$env:DB_USERNAME = "<계정>"; $env:DB_PASSWORD = "<비밀번호>"; .\gradlew.bat bootRun
```

서버는 `8080` 포트에서 기동합니다. IntelliJ에서는 `TicketoApplication`을 직접 실행해도 됩니다.

### 5. 동작 확인 예시

```http
POST http://localhost:8080/api/members
Content-Type: application/json

{ "email": "test@example.com", "password": "password123", "name": "테스트" }
```

```http
POST http://localhost:8080/api/members/login
Content-Type: application/json

{ "email": "test@example.com", "password": "password123" }
```

## 동시성 이슈 해결 과정

> 3주차 예정 (Reservation 기본 기능 완성 후 진행)

계획한 순서는 다음과 같습니다. 결과는 구현 후 이곳에 표로 정리합니다.

1. 문제 재현: 잔여 10석에 스레드 100개가 1석씩 예매하는 테스트
2. 조건부 UPDATE로 해결
3. 비관적 락(`SELECT ... FOR UPDATE`)으로 해결 후 비교

| 방식 | 결과 | 성능 | 코드 복잡도 |
|---|---|---|---|
| 락 없음 (문제 재현) | - | - | - |
| 조건부 UPDATE | - | - | - |
| 비관적 락 | - | - | - |
