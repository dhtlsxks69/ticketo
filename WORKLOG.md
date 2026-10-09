# Ticketo 작업 일지

그날 실제로 한 일을 날짜별로 기록합니다. 앞으로의 일정은 `ticketo-plan.md`를 참고합니다.
작업 종료 시 해당 날짜에 한 일과 커밋을 추가합니다.

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
  - 동시성 처리 없는 기본 버전. 앱 기동 확인과 HTTP 호출 검증은 아직 안 함 (controller 단계에서 진행)
- Reservation 예매 취소 service 추가: `ReservationService.cancel` (예매 없음 404, 이미 취소됨 409, 상태 CANCELED 변경 후 잔여 수량 복구) (`a7ca37b`)
  - 앱 기동 확인과 HTTP 호출 검증은 아직 안 함

## 10/8 (목)

- `.idea`는 이미 추적 해제 상태(`git ls-files .idea` 결과 없음) 확인
- `schema.sql`에 reservation 테이블 추가 (ticket은 기존 테이블이 있고 `TicketMapper.xml`이 현재 컬럼명을 사용 중이라 유지)
- 앱 기동 후 member/ticket/reservation 테이블 생성 확인 (`5fa6918`)

## 회고 메모

- 로그인 시 이메일 존재 여부에 따른 응답 시간 차이는 보안 고도화 때 검토
- JWT 전에는 로그인 여부를 다른 API가 구분하지 못함
- 예전 DB 기본값이 git 히스토리에 남아 있음 (비밀번호를 실제로 쓴다면 DB에서 변경 필요)
