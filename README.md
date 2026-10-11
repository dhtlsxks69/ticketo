# Ticketo

공연 티켓 예매 서비스의 백엔드 API 프로젝트입니다. 회원, 공연(티켓), 예매 도메인으로 구성하며,
**잔여 수량 기반 좌석 관리**와 **예매 시 발생하는 동시성 문제의 재현과 해결 과정**을 정리하는 것이 목표입니다.

> 현재는 회원(가입/로그인), 공연(CRUD), 예매(예매/취소/내 예매 내역) 도메인까지 구현되어 있습니다. 동시성 처리(조건부 UPDATE, 비관적 락 비교)와 JWT 인증까지 구현했습니다.

## 기술 스택

| 구분 | 내용 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.6 |
| Build | Gradle (Kotlin DSL) |
| Database | MySQL 8.0 (utf8mb4) |
| Persistence | MyBatis 3.0.5 starter (SQL은 XML 매퍼에 작성) |
| 인증 | Spring Security(JWT 필터, STATELESS) + jjwt 0.12.6 (HS256 액세스 토큰) |
| 비밀번호 해시 | BCrypt |
| 기타 | Lombok, Spring Validation |

## 구현 현황

- [x] 회원가입 (이메일 정규화, 중복 검사, BCrypt 해시 저장)
- [x] 로그인 (이메일/비밀번호 확인, 실패 시 401 통일 메시지)
- [x] 회원 목록 조회
- [x] 공연 등록 / 목록 / 상세 / 수정 / 삭제
- [x] 수량 기반 잔여 좌석 관리 (`total_quantity` / `remaining_quantity`)
- [x] 회원 단건 조회 / 수정 (이름만 수정 가능)
- [x] 예매 (회원/공연 존재 확인, 잔여 수량 부족 시 409, 잔여 수량 차감)
- [x] 예매 취소 (상태를 CANCELED로 변경, 잔여 수량 복구, 중복 취소 409)
- [x] 내 예매 내역 조회 (최신순, 취소된 예매 포함)
- [x] 동시성 처리: 문제 재현 → 조건부 UPDATE → 비관적 락 비교
- [x] 공통 예외 처리, 오류 응답 포맷 통일
- [x] JWT 인증 (로그인 시 액세스 토큰 발급, 예매/회원 수정/공연 쓰기는 로그인 필요, 본인 데이터만 접근)

## API 목록

컨트롤러에 실제로 구현된 API만 정리했습니다.

### Member

| 메서드 | 경로 | 설명 | 응답 |
|---|---|---|---|
| GET | `/api/members` | 회원 목록 조회 (로그인 필요) | 200 / 401 |
| GET | `/api/members/{id}` | 회원 단건 조회 (본인만) | 200 / 401 / 403 / 404 |
| POST | `/api/members` | 회원가입 | 201 / 409(이메일 중복) |
| PUT | `/api/members/{id}` | 회원 수정 (본인만, 이름만) | 200 / 400 / 401 / 403 / 404 |
| POST | `/api/members/login` | 로그인 (액세스 토큰 발급) | 200 / 401 |

- 비밀번호는 응답에 포함되지 않습니다.
- 수정은 `name`만 가능합니다. 이메일은 로그인 ID이고, 비밀번호 변경은 현재 비밀번호 확인 절차가 필요해 아직 제공하지 않습니다. 요청에 다른 필드가 있어도 무시됩니다.
- 로그인 실패는 이메일 없음/비밀번호 불일치 모두 같은 401 메시지로 응답합니다.

### Ticket

| 메서드 | 경로 | 설명 | 응답 |
|---|---|---|---|
| GET | `/api/tickets` | 공연 목록 (공연 일시 오름차순) | 200 |
| GET | `/api/tickets/{id}` | 공연 상세 (잔여 수량 포함) | 200 / 404 |
| POST | `/api/tickets` | 공연 등록 (로그인 필요) | 201 / 401 |
| PUT | `/api/tickets/{id}` | 공연 수정 (로그인 필요) | 200 / 401 / 404 / 409(판매된 수량보다 총 수량을 줄이는 경우) |
| DELETE | `/api/tickets/{id}` | 공연 삭제 (로그인 필요) | 204 / 401 / 404 / 409(예매 내역이 있는 공연) |

- 잔여 수량은 서버가 관리하며, 등록 시 총 수량으로 초기화됩니다. 요청 값은 무시됩니다.
- 예매 내역이 있는 공연은 삭제할 수 없습니다. 취소된 예매도 내역으로 남아 있으므로 모두 삭제 불가입니다.

### Reservation

| 메서드 | 경로 | 설명 | 응답 |
|---|---|---|---|
| GET | `/api/reservations` | 내 예매 내역 (로그인 필요, 최신순, 취소된 예매 포함) | 200 / 401 |
| POST | `/api/reservations` | 예매 (로그인 필요, 잔여 수량 차감) | 201 / 400(검증 실패) / 401 / 404(공연 없음) / 409(잔여 수량 부족) |
| DELETE | `/api/reservations/{id}` | 예매 취소 (로그인 필요, 본인 예매만, 잔여 수량 복구) | 200 / 401 / 404 / 409(이미 취소됨) |

- 예매 취소는 행을 삭제하지 않고 `status`를 `CANCELED`로 바꾼 뒤 그 예매를 응답합니다.
- `status`는 서버가 관리하며(`RESERVED` / `CANCELED`) 요청 값은 무시됩니다. DB에는 CHECK 제약이 걸려 있습니다.
- 예매한 회원은 토큰에서 가져옵니다. 요청 본문의 `memberId`는 무시됩니다. 다른 회원의 예매를 취소하려 하면 존재 여부를 알려주지 않도록 없는 예매와 같은 404로 응답합니다.
- 예매는 잔여 수량을 조건부 UPDATE로 차감하므로 동시 요청에도 초과 판매가 생기지 않습니다. (아래 "동시성 이슈 해결 과정" 참고)

## 인증 (JWT)

1. `POST /api/members/login`으로 로그인하면 응답에 액세스 토큰이 포함됩니다.

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "member": { "id": 1, "email": "test@example.com", "name": "테스트", "createdAt": "..." }
}
```

2. 로그인이 필요한 API는 `Authorization: Bearer <accessToken>` 헤더와 함께 호출합니다.

| 구분 | API |
|---|---|
| 공개 | 회원가입, 로그인, 공연 목록/상세 조회 |
| 로그인 필요 | 예매/취소/내 예매 내역, 회원 목록/단건 조회/수정, 공연 등록/수정/삭제 |
| 본인만 | 회원 단건 조회/수정(`403`), 예매 취소(`404`) |

- 토큰이 없거나 만료/위조된 경우 `401`, 다른 회원의 정보에 접근하면 `403`으로 응답합니다.
- 토큰 유효 시간은 기본 60분이며 `jwt.access-token-expiration-minutes`로 조정할 수 있습니다. 리프레시 토큰은 아직 없습니다.
- 공연 등록/수정/삭제는 관리자 구분이 아직 없어 로그인한 회원이라면 누구나 할 수 있습니다. 회원 목록도 마찬가지입니다. (역할 도입 시 제한 예정)

## 오류 응답 형식

모든 오류는 `GlobalExceptionHandler`가 아래 형식으로 통일해서 응답합니다. `fieldErrors`는 입력값 검증 실패(400)일 때만 포함됩니다.

```json
{
  "timestamp": "2026-10-11T11:02:04.9647487",
  "status": 400,
  "error": "Bad Request",
  "message": "입력값이 올바르지 않습니다.",
  "path": "/api/reservations",
  "fieldErrors": [
    { "field": "quantity", "message": "1 이상이어야 합니다" }
  ]
}
```

| 상태 | 발생 상황 |
|---|---|
| 400 | 입력값 검증 실패, 깨진 JSON/타입 오류, 필수 파라미터 누락, 경로 변수 형식 오류 |
| 401 / 403 | 토큰이 없거나 유효하지 않음(401), 본인 외 회원 정보 접근(403) |
| 404 / 409 | 서비스가 던진 `ResponseStatusException`의 사유를 `message`로 전달 |
| 404 / 405 / 415 | 없는 경로, 허용되지 않는 메서드, 지원하지 않는 Content-Type |
| 503 | 데드락 등 DB 락 충돌로 트랜잭션이 롤백된 경우 (재시도 안내) |
| 500 | 예상하지 못한 예외. 내부 정보는 응답에 노출하지 않고 서버 로그에만 남김 |

## 패키지 구조

```text
com.ticketo
├── config              # 전역 설정 (PasswordConfig 등)
├── com                 # 공통 컴포넌트
├── member
│   ├── controller      # MemberController
│   ├── service         # MemberService
│   ├── mapper          # MemberMapper
│   └── vo              # MemberVO, MemberLoginVO, MemberUpdateVO
├── ticket
│   ├── controller      # TicketController
│   ├── service         # TicketService
│   ├── mapper          # TicketMapper
│   └── vo              # TicketVO
└── reservation
    ├── controller      # ReservationController
    ├── service         # ReservationService
    ├── mapper          # ReservationMapper
    └── vo              # ReservationVO

src/main/resources
├── application.yml
├── schema.sql          # 기동 시 실행되는 테이블 생성 스크립트
└── mapper/{feature}/{Feature}Mapper.xml
```

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
이미 만들어진 테이블은 `schema.sql`을 고쳐도 바뀌지 않으므로, 변경분은 `ALTER TABLE`로 직접 반영하거나 테이블을 `DROP`한 뒤 재기동하세요.

### 3. 접속 정보 설정

`src/main/resources/application.yml`은 아래 환경변수로 DB 접속 정보를 주입받습니다.

| 환경변수 | 설명 |
|---|---|
| `DB_HOST` | DB 호스트 (미지정 시 `localhost`) |
| `DB_PORT` | DB 포트 (미지정 시 `3306`) |
| `DB_NAME` | 데이터베이스 이름 (미지정 시 `ticketo`) |
| `DB_USERNAME` | **필수, 직접 채워야 하는 항목** — DB 계정 |
| `DB_PASSWORD` | **필수, 직접 채워야 하는 항목** — DB 비밀번호 |

| `JWT_SECRET` | **필수** — JWT 서명 비밀키. HS256 요구사항에 따라 32자(256비트) 이상이어야 합니다. |

`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`은 기본값이 없어서 지정하지 않으면 앱이 기동되지 않습니다.
(`JWT_SECRET`이 없거나 너무 짧으면 기동 시점에 오류가 납니다. 테스트는 Gradle이 테스트 전용 값을 주입합니다.)
본인의 환경에 맞는 값을 환경변수로 지정해서 사용하세요. 실제 비밀번호는 저장소에 커밋하지 마세요.

### 4. 실행

```bash
# macOS / Linux / Git Bash
DB_USERNAME=<계정> DB_PASSWORD=<비밀번호> JWT_SECRET=<32자 이상의 비밀키> ./gradlew bootRun
```

```powershell
# Windows PowerShell
$env:DB_USERNAME = "<계정>"; $env:DB_PASSWORD = "<비밀번호>"; $env:JWT_SECRET = "<32자 이상의 비밀키>"; .\gradlew.bat bootRun
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

이후 요청에는 로그인 응답의 `accessToken`을 헤더로 보냅니다.

```http
POST http://localhost:8080/api/reservations
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "ticketId": 1, "quantity": 2 }
```

```http
GET http://localhost:8080/api/reservations
Authorization: Bearer <accessToken>
```

```http
DELETE http://localhost:8080/api/reservations/1
Authorization: Bearer <accessToken>
```

## 동시성 이슈 해결 과정

`ReservationConcurrencyTest`로 스레드 100개가 1매씩 동시에 예매하는 상황을 재현하고, 방식별로 비교했습니다.
(로컬 MySQL 8.0, 커넥션 풀 기본값, 단일 공연에 요청이 몰리는 경우 기준)

### 1. 문제 재현 (락 없음)

잔여 10매에 100개 요청을 보내면 초과 판매는 발생하지 않았지만, 요청의 8~9%(8~9건)가 데드락(`DeadlockLoserDataAccessException`)으로 500 오류가 되었습니다.

- 원인(코드 순서와 InnoDB 락 규칙에 따른 추정): 예매 저장(`INSERT reservation`)이 FK 검사로 ticket 행에 공유 락을 먼저 잡고, 이후 `UPDATE ticket`이 배타 락을 요구하면서 서로의 공유 락을 기다리는 교착이 발생합니다.
- DB의 `chk_ticket_quantity` CHECK 제약이 음수 재고를 막고 있어 순수한 초과 판매는 가려졌습니다.

### 2. 조건부 UPDATE

```sql
UPDATE ticket SET remaining_quantity = remaining_quantity - #{quantity}
WHERE id = #{id} AND remaining_quantity >= #{quantity}
```

차감을 예매 INSERT보다 먼저 실행하고, 영향받은 행이 0이면 매진(409)으로 처리합니다. ticket 배타 락을 먼저 잡기 때문에 교착 경로가 사라졌습니다. 현재 컨트롤러가 사용하는 방식입니다.

### 3. 비관적 락

`SELECT ... FOR UPDATE`로 ticket 행을 잠근 뒤 잔여 수량을 확인하고 차감합니다. (`ReservationService.reserveWithPessimisticLock`, 비교용)

### 비교 결과

| 방식 | 결과 | 성능 (스레드 100개, 3회) | 코드 복잡도 |
|---|---|---|---|
| 락 없음 (문제 재현) | 잔여 10매: 성공 10, 매진 81, 데드락 500 오류 8~9건 | 미측정 | 가장 단순 |
| 조건부 UPDATE | 잔여 10매: 성공 10, 매진 90, 오류 0<br>잔여 100매: 성공 100, 오류 0 | 잔여 10매 63~69ms<br>잔여 100매 257~300ms | SQL 한 줄 추가, 조회 후 비교 로직 불필요 |
| 비관적 락 | 잔여 10매: 성공 10, 매진 90, 오류 0<br>잔여 100매: 성공 100, 오류 0 | 잔여 10매 53~59ms<br>잔여 100매 259~283ms | 잠금 조회 + 확인 + 차감 로직이 따로 필요 |

- 두 해결 방식 모두 초과 판매, 수량 불일치, 오류가 없었습니다.
- 속도 차이는 오차 범위입니다. 회차마다 순위가 뒤집혔고, 이 규모에서는 어느 쪽이 빠르다고 말할 수 없습니다.
- 한계: 로컬 DB 하나, 단일 공연, 테스트 실행 순서 고정(조건부 UPDATE가 항상 먼저 실행) 조건의 결과입니다. 공연이 여러 개이거나 트랜잭션이 길어지면 달라질 수 있습니다.
