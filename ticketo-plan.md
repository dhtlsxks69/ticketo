# Ticketo 설계 및 작업 계획

## 1. 프로젝트 규칙 (claude.md에 붙여넣기용)

- 스택: Java 21, Spring Boot 3.5.6, MySQL, MyBatis, Gradle
- 패키지 구조: `domain/{member,ticket,reservation}/{controller,service,mapper,vo}`
- MyBatis XML은 `src/main/resources/mapper/{도메인}Mapper.xml`
- 한 번에 한 도메인, 한 계층씩 작업한다. 단계가 끝나면 실행 확인 후 커밋한다.
- 좌석은 좌석 번호 방식이 아니라 **수량 기반**(total_seats / remaining_seats)으로 관리한다.
- 인증은 1차에서 이메일/비밀번호 확인(BCrypt)까지만. JWT는 마지막 단계에서 추가한다.
- 커밋 메시지 예: `feat: Ticket 등록/조회 API 추가`

## 2. 테이블 설계 (schema.sql 초안)

```sql
CREATE TABLE member (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,          -- BCrypt 해시
    name        VARCHAR(50)  NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ticket (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(200) NOT NULL,
    venue            VARCHAR(200) NOT NULL,
    event_date       DATETIME     NOT NULL,
    price            INT          NOT NULL,
    total_seats      INT          NOT NULL,
    remaining_seats  INT          NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (remaining_seats >= 0)
);

CREATE TABLE reservation (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id    BIGINT      NOT NULL,
    ticket_id    BIGINT      NOT NULL,
    quantity     INT         NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'RESERVED',  -- RESERVED / CANCELED
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    canceled_at  DATETIME    NULL,
    FOREIGN KEY (member_id) REFERENCES member(id),
    FOREIGN KEY (ticket_id) REFERENCES ticket(id)
);
```

## 3. API 목록

| 도메인 | 메서드 | 경로 | 설명 |
|---|---|---|---|
| Member | POST | /members | 회원가입 |
| Member | POST | /members/login | 로그인 (JWT 전까지는 성공/실패 응답만) |
| Member | GET | /members/{id} | 내 정보 조회 |
| Member | PUT | /members/{id} | 내 정보 수정 |
| Ticket | POST | /tickets | 공연 등록 |
| Ticket | GET | /tickets | 목록 조회 |
| Ticket | GET | /tickets/{id} | 상세 조회 (잔여 좌석 포함) |
| Ticket | PUT | /tickets/{id} | 수정 |
| Ticket | DELETE | /tickets/{id} | 삭제 |
| Reservation | POST | /reservations | 예매 (잔여 좌석 차감) |
| Reservation | DELETE | /reservations/{id} | 예매 취소 (좌석 복구) |
| Reservation | GET | /reservations?memberId= | 내 예매 내역 |

JWT 도입 전까지는 `memberId`를 요청 파라미터/바디로 받고, JWT 단계에서 토큰에서 꺼내도록 바꾼다.

## 4. 작업 일정

평일은 하루 1시간, 주말은 여유 있게 진행한다.

| 회차 | 시간 | 목표 | 완료 기준 |
|---|---|---|---|
| 오늘 저녁 | 1h | 정리 + 스키마 | `.idea` 추적 해제, claude.md 갱신, schema.sql에 ticket/reservation 추가, 앱 기동 확인 |
| 평일 2 | 1h | Ticket 등록/조회 | vo, mapper, service 완성, 서비스 단위로 등록 후 조회 확인 |
| 평일 3 | 1h | Ticket controller + 수정/삭제 | Postman으로 5개 API 호출 성공 |
| 평일 4 | 1h | Member 로그인 | BCrypt로 가입 시 암호화, 로그인 성공/실패 확인 |
| 주말 1 | 2~3h | Reservation 기본 | 예매/취소/내역 조회 동작. 이 단계는 동시성 처리 없이 만든다 |
| 주말 2 | 2~3h | 동시성 | 스레드 100개 예매 테스트로 문제 재현, 조건부 UPDATE로 해결, 비관적 락으로 비교 |
| 이후 | - | 고도화 | 예외 처리 공통화, JWT, README 정리 |

## 5. 동시성 단계 미리보기

1. **문제 재현**: 잔여 10석에 스레드 100개가 1석씩 예매하면 잔여가 음수가 되거나 예매 건수와 어긋난다.
2. **조건부 UPDATE**: `UPDATE ticket SET remaining_seats = remaining_seats - #{qty} WHERE id = #{id} AND remaining_seats >= #{qty}`. 영향받은 행이 0이면 매진 처리한다.
3. **비관적 락**: `SELECT ... FOR UPDATE`로 조회 후 차감하고 성능과 코드 복잡도를 비교한다.
4. 결과를 README에 표로 정리한다. 포트폴리오에서 가장 강한 부분이다.

## 6. 바이브코딩 요청 예시

- "claude.md를 읽고, Ticket 도메인의 vo와 mapper 인터페이스, mapper XML만 만들어줘. service는 아직 만들지 마."
- "Member의 기존 코드 패턴에 맞춰서 TicketService에 등록/조회 메서드를 추가해줘."
- "방금 만든 코드가 어떻게 동작하는지 흐름을 설명해줘." (이해하고 넘어가기)
- 에러가 나면 에러 로그 전체와 관련 파일을 같이 붙여서 질문한다.
