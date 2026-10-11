# Ticketo 작업 일지

그날 실제로 한 일을 날짜별로 기록합니다. 앞으로의 일정은 `ticketo-plan.md`를 참고합니다.
작업 종료 시 해당 날짜에 한 일과 커밋을 추가합니다.

## 10/11 (일)

- Reservation controller 추가: `POST /api/reservations`, `DELETE /api/reservations/{id}`(취소, CANCELED 예매 반환), `GET /api/reservations?memberId=` (`3337127`)
  - curl로 HTTP 검증 완료: 예매 201·잔여 차감, 수량 초과 409, 없는 회원/공연 404, quantity 0·memberId 누락 400, 취소 시 잔여 복구, 중복 취소 409, 없는 예매 404
  - `ReservationMapper.java`에 섞여 있던 오타 문자 제거
  - Windows 셸에서 한글 JSON을 curl로 보내면 400이 나서 ASCII 값으로 검증 (Postman 등에서는 확인 필요)
- `schema.sql`의 reservation에 `chk_reservation_status` CHECK 제약 추가 (`status IN ('RESERVED','CANCELED')`)
  - `CREATE TABLE IF NOT EXISTS`라 기존 DB에는 ALTER로 직접 적용 후 재기동, 예매 API 정상 동작 확인
- README에 Reservation API/패키지 구조/구현 현황 반영
- 동시성 문제 재현 테스트 추가: `ReservationConcurrencyTest` (잔여 10매, 스레드 100개가 1매씩 동시 예매)
  - 3회 실행 결과: 초과 판매·수량 불일치는 발생하지 않았고 성공 10건, 매진(409) 81건 고정
  - 대신 나머지 8~9건이 `DeadlockLoserDataAccessException`(500)으로 실패. reservation INSERT의 FK 공유 락 때문에 `UPDATE ticket`의 배타 락과 교착이 발생하는 것으로 추정
  - `ticket`의 `chk_ticket_quantity` CHECK 제약이 음수 재고를 막고 있어 순수한 초과 판매는 가려진 상태 (첫 실행에서 `UncategorizedSQLException` 1건은 원인 미확인)
  - 다음 단계: 조건부 UPDATE로 해결 후 같은 테스트로 비교
- 조건부 UPDATE로 동시성 해결: `TicketMapper.decreaseTicketRemainingQuantity`(`WHERE remaining_quantity >= #{quantity}`) 추가, `reserve`에서 reservation INSERT보다 먼저 실행하고 0건이면 409 (`7e7f32c`)
  - 같은 테스트 3회 재실행: 성공 10 / 매진(409) 90 / 데드락 0 (수정 전: 성공 10 / 409 81 / 데드락 8~9)
  - 배타 락을 먼저 잡아 FK 공유 락과의 교착 경로가 사라진 것으로 판단 (InnoDB 락 로그로 직접 확인하지는 않음)
  - 취소(`cancel`) 락 순서 점검: reservation → ticket 순으로 예매와 반대지만, 예매가 INSERT하는 새 행은 커밋 전이라 취소가 볼 수 없어 교착 사이클이 없다고 판단 (코드 분석만, 실행 검증은 하지 않음)
- 공통 예외 처리 추가: `GlobalExceptionHandler`가 `PessimisticLockingFailureException`(데드락 등)을 500 대신 503으로 응답 (`bf1b3a6`)
  - 데드락이 더 이상 재현되지 않아 이 핸들러를 타는 실제 요청은 검증하지 못함. 앱 컨텍스트 로딩만 확인
- 비관적 락 예매 추가 및 비교: `TicketMapper.selectTicketByIdForUpdate`(`SELECT ... FOR UPDATE`), `ReservationService.reserveWithPessimisticLock` (비교용, 컨트롤러는 조건부 UPDATE 버전 사용) (`4c40f91`)
  - `ReservationConcurrencyTest`를 두 방식 비교용으로 확장 (잔여 10매/100매, 스레드 100개, 소요 시간 출력)
  - 3회 결과: 두 방식 모두 초과 판매·불일치·오류 0. 잔여 10매 조건부 UPDATE 63~69ms / 비관적 락 53~59ms, 잔여 100매 257~300ms / 259~283ms로 오차 범위
  - 한계: 로컬 DB 하나, 단일 공연, 테스트 실행 순서 고정(조건부 UPDATE가 항상 먼저)
- README 동시성 섹션에 재현 → 조건부 UPDATE → 비관적 락 비교 표 정리, 구현 현황 갱신 (`08c40ca`)
- 공통 예외 처리 확장: `ErrorResponse` 추가, `GlobalExceptionHandler`가 `ResponseEntityExceptionHandler`를 상속해 모든 오류를 같은 JSON 형식으로 응답 (`cc4efb1`)
  - `ResponseStatusException` 사유를 `message`로 노출, `@Valid` 실패는 `fieldErrors` 포함, 깨진 JSON/파라미터 누락/타입 오류는 한글 메시지, 예상 못한 예외는 500 + 일반 메시지(내부 정보 미노출, 로그만)
  - bootRun 후 curl로 400(검증/JSON/타입/파라미터)·401·404·405·409·415·500 응답 확인
  - 첫 구현에서 `ResponseStatusException` 사유가 "Not Found"로 덮어써지고 경로 변수 오류가 영어로 나와 `handleErrorResponseException`, `handleTypeMismatch`를 오버라이드해 수정
  - 503(락 충돌) 경로는 데드락이 재현되지 않아 이번에도 실제 요청으로 검증하지 못함
  - 부수 확인: FK가 걸린 공연 삭제(`DELETE /api/tickets/{id}`)는 500으로 응답. 409로 바꾸는 것은 별도 작업 후보

## 10/9 (금)

- Ticket 등록/목록/상세/수정/삭제 API 구현 (`79524dd`)
- `ticketo-plan.md` 컬럼명·API 경로, `claude.md` XML 경로를 실제 코드 기준으로 정리 (`a7c93b8`)
- Member 로그인 API 구현: `POST /api/members/login` (BCrypt matches, 이메일 정규화, 401 통일 메시지) (`12ef5e3`)
  - Postman으로 성공/실패 응답 확인
- README.md 작성 (`1a7e453`)
- `application.yml`의 DB 계정/비밀번호 기본값 제거, README 설명 수정 (`b330b54`)
- Reservation vo, mapper 추가: `ReservationVO`, `ReservationMapper`(조회/목록/등록/취소 처리) + XML (`a92e478`)
  - 앱 기동 확인 (mapper XML 로딩 오류 없음). 쿼리 실행 검증은 예매 service 단계에서 진행
- Reservation 예매 service 추가: `ReservationService.reserve` (회원/공연 존재 확인 404, 잔여 수량 부족 409, 예매 저장 후 잔여 수량 차감) (`59ad8c4`)
  - `TicketMapper`에 잔여 수량 증감 쿼리(`updateTicketRemainingQuantity`) 추가
  - 동시성 처리 없는 기본 버전. 앱 기동 확인 완료, HTTP 호출 검증은 controller 단계에서 진행
- Reservation 예매 취소 service 추가: `ReservationService.cancel` (예매 없음 404, 이미 취소됨 409, 상태 CANCELED 변경 후 잔여 수량 복구) (`a7ca37b`)
  - 앱 기동 확인 완료, HTTP 호출 검증은 controller 단계에서 진행
- Reservation 내 예매 내역 조회 service 추가: `ReservationService.getReservationList` (회원 없음 404, 최신순, 취소된 예매 포함) (`5adf394`)
  - bootRun으로 앱 기동 확인 완료. HTTP 호출 검증은 controller 단계에서 진행

## 10/8 (목)

- `.idea`는 이미 추적 해제 상태(`git ls-files .idea` 결과 없음) 확인
- `schema.sql`에 reservation 테이블 추가 (ticket은 기존 테이블이 있고 `TicketMapper.xml`이 현재 컬럼명을 사용 중이라 유지)
- 앱 기동 후 member/ticket/reservation 테이블 생성 확인 (`5fa6918`)

## 회고 메모

- 로그인 시 이메일 존재 여부에 따른 응답 시간 차이는 보안 고도화 때 검토
- JWT 전에는 로그인 여부를 다른 API가 구분하지 못함
- 예전 DB 기본값이 git 히스토리에 남아 있음 (비밀번호를 실제로 쓴다면 DB에서 변경 필요)
