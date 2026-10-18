# Ticketo 작업 일지

그날 실제로 한 일을 날짜별로 기록합니다. 앞으로의 일정은 `ticketo-plan.md`를 참고합니다.
작업 종료 시 해당 날짜에 한 일과 커밋을 추가합니다.

## 10/15 (목)

- 부하 테스트 1단계: 환경 준비 파일 작성 (브랜치 `feature/loadtest`, SQL/런북 `9c185ce`, `.gitignore` `a1cf02c`)
  - 추가: `loadtest/README.md`(런북), `loadtest/sql/01_seed_members.sql`, `02_seed_tickets.sql`, `03_reset.sql`, `04_verify.sql`, `99_cleanup.sql`. `.gitignore` 끝에 `loadtest/results/` 추가(UTF-8, 기존 내용 유지)
  - 방식: 전용 DB `ticketo_loadtest`에서 `TicketoApplication` 복제 설정(`Ticketo-loadtest`, `DB_NAME=ticketo_loadtest`)으로 앱을 한 번 띄워 `schema.sql`이 테이블을 만들게 함. 개발용 설정은 수정하지 않음. SQL은 IntelliJ Database 도구/MySQL 클라이언트에서 실행(PowerShell `<` 리다이렉션은 런북에 쓰지 않음)
  - 회원: 회원가입 API로 시드 회원 1명(`loadtest-seed@example.com`)을 만든 뒤 `01_seed_members.sql`이 그 BCrypt 해시를 복제해 `loadtest-00001@example.com ~` 생성. 모든 시나리오는 `quantity=1` 고정
  - 모든 SQL에 접속 DB가 `ticketo_loadtest`가 아니면 오류로 중단하는 안전장치 포함
  - 작성 시점에는 앱 기동과 SQL 실행을 하지 않았고, 실행 확인(재귀 CTE INSERT, 가드 쿼리 포함)은 아래 "부하 테스트 1단계 실행 확인" 항목에 기록. k6 스크립트와 컨트롤러 변경은 다음 단계
- 부하 테스트 1단계 수정 (`9c185ce`)
  - 모든 `loadtest/sql/*.sql`의 첫 실행 문장을 `USE ticketo_loadtest;`로 하고 DB 확인 쿼리는 그 뒤에 유지
  - 회원 기본 100명으로 변경, 분산용 공연 `loadtest-spread-001~200`(각 100,000매, 재귀 CTE + `cte_max_recursion_depth` 100000 설정)으로 확대
  - `05_seed_fillers.sql` 추가(`loadtest-filler-NNNNN`, 개수는 `@filler_count`, 각 100매). title에 인덱스가 없어 NOT EXISTS 대신 기존 개수 이후 번호만 추가하는 방식. `04_verify.sql`은 filler를 제외하고 filler 예매·수량 변동이 없는지 확인, `99_cleanup.sql`은 filler도 삭제
  - 삭제/갱신 범위 점검: `03_reset.sql`, `99_cleanup.sql`의 모든 DELETE/UPDATE는 이미 `loadtest-` 접두어 조건(회원 이메일 `loadtest-%@example.com`, 공연 제목 `loadtest-%`)이 있었음. reset의 UPDATE에는 수량이 달라진 행만 갱신하도록 조건 추가
  - README에 Workbench/DBeaver/`mysql -e "source ..."` 방법과 `--default-character-set=utf8mb4` 옵션 추가
  - 수정 시점에는 앱 기동과 SQL 실행을 하지 않았고, 실행 확인은 아래 "부하 테스트 1단계 실행 확인" 항목에 기록
- `04_verify.sql` 출력 간소화 (`9c185ce`): 공연별 전체 목록 대신 불일치/초과 판매 공연만 출력(0행이면 통과), 파일 끝에 전체 요약 한 줄(공연 수, 불일치·초과 판매 공연 수, total/remaining/reserved 합계) 추가. filler·상태별·quantity 검증 쿼리는 그대로. 수정 시점에는 SQL을 실행하지 않았고, 실행 확인은 아래 "부하 테스트 1단계 실행 확인" 항목에 기록

- 부하 테스트 1단계 실행 확인
  - 전용 DB `ticketo_loadtest` 생성, `Ticketo-loadtest` 실행 설정으로 앱을 띄워 테이블 생성, 회원가입 API로 시드 회원 가입
  - 수정 전 버전 SQL로 1차 실행(회원 1,001명, 공연 7개)한 뒤 `99_cleanup.sql`로 정리. 시드 회원도 함께 삭제되어 시드 회원을 다시 가입함
  - 수정 버전 `01_seed_members.sql`, `02_seed_tickets.sql` 실행 결과: 회원 101명, 공연 202개, 예매 0건
  - `04_verify.sql` 통과. 개발 DB에는 `loadtest-` 데이터 0건 확인
  - 알게 된 점: PowerShell에서는 `mysql < 파일` 리다이렉션을 쓸 수 없어 `mysql -e "source ..."` 사용, `mysql` 전체 경로 지정 필요, `--default-character-set=utf8mb4` 옵션 필요

## 10/14 (수)

- 인증 동작 자동 테스트 추가: `src/test/java/com/ticketo/com/AuthSecurityTest.java` (`07e396b`)
  - 방식: `@SpringBootTest` + `@AutoConfigureMockMvc`로 `SecurityConfig`, `JwtAuthenticationFilter`가 실제 적용된 상태에서 요청. 403/404 소유자 검사는 서비스와 DB가 있어야 의미가 있어 `@WebMvcTest` + 서비스 mock 대신 선택
  - 데이터: `JdbcTemplate`으로 회원 2명(A, B)·공연 1개·B의 예매 1건을 INSERT, 이메일 접두어 `auth-test-`/공연 제목 `auth-test`로 구분해 `@BeforeEach`와 `@AfterEach`에서 reservation → ticket → member 순으로 삭제. 롤백 방식은 쓰지 않음
  - 시나리오: 토큰 없음/위조/만료 401(예매·취소·내 예매 내역), 타인 id 회원 조회·수정 403(이름 변경 안 됨 확인), 타인 예매 취소 404(상태·잔여 수량 유지 확인), 본문 memberId를 넣어도 토큰 회원으로 예매(응답과 DB 확인), 회원가입·로그인·공연 목록/상세 토큰 없이 호출 가능. 정상 경로(본인 조회 200, 본인 취소 200)도 함께 확인
  - 위조 토큰은 다른 비밀키로, 만료 토큰은 같은 키(`JWT_SECRET`)로 과거 만료 시각을 넣어 jjwt로 직접 생성
  - `ReservationConcurrencyTest`는 수정하지 않음. `compileTestJava`만 통과를 확인했고 앱 기동과 테스트 실행은 하지 않음 (IntelliJ에서 실행 예정, `DB_USERNAME`/`DB_PASSWORD` 필요)
  - IntelliJ에서 AuthSecurityTest 전체 통과 확인

## 10/12 (월)

- JPA 잔재 정리 (MyBatis만 사용) (`e730089`)
  - 사용처 확인: `BaseTimeEntity`·`JpaAuditingConfig`는 서로의 Javadoc에서만 참조, `jakarta.persistence` import는 `BaseTimeEntity` 한 곳, `@Entity`·Spring Data Repository 사용 없음
  - `mybatis-spring-boot-starter`가 `spring-boot-starter-jdbc`(HikariCP, spring-jdbc, spring-tx)를 포함해 트랜잭션 관리자는 그대로 유지됨을 의존성 트리로 확인
  - 삭제: `BaseTimeEntity.java`, `JpaAuditingConfig.java`, `build.gradle.kts`의 `spring-boot-starter-data-jpa`와 주석, `application.yml`의 `spring.jpa` 섹션과 `org.hibernate` 로깅 설정
  - `compileJava`, `compileTestJava` 통과. IntelliJ에서 앱 기동, 로그인·예매·취소, ReservationConcurrencyTest 확인 완료
  - 참고: `TicketoApplication.java` 주석에 "JPA" 언급이 남아 있어 별도로 정리. 13행 "Web, JPA, MySQL 등"을 "Web, MyBatis, MySQL 등"으로, 27행 "DataSource/JPA 초기화"를 "DataSource/MyBatis 초기화"로 수정 (주석만 변경, `compileJava` 통과)
- README 점검 및 수정 (코드는 수정하지 않음)
  - 패키지 구조에 `com/GlobalExceptionHandler`, `com/ErrorResponse`, `member/vo/MemberLoginResponseVO` 추가 (실제 파일 위치 `git ls-files`로 확인)
  - 환경변수 설명: `DB_*`는 `application.yml`이 읽고, `JWT_SECRET`은 `JwtTokenProvider`가 `@Value`로 코드에서 직접 읽는다는 점을 구분해서 표기
  - API 표에 `@Valid` 검증 실패(400) 누락분 보완 (회원가입, 로그인, 공연 등록/수정). 동시성 섹션의 "교착 경로가 사라졌습니다"를 WORKLOG와 같게 "사라진 것으로 판단(락 로그 미확인)"으로 완화
  - 확신 없음: 오류 표의 "없는 경로 404 / 405"는 `anyRequest().authenticated()` 때문에 토큰이 없으면 401이 먼저 나올 것으로 보이나 실행으로 확인하지 않아 고치지 않음. 동시성 표의 수치는 WORKLOG 기록 기준이며 재실행하지 않음

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
- Member 단건 조회/수정 추가: `GET /api/members/{id}`, `PUT /api/members/{id}` (이름만 수정) (vo/mapper `6334328`, service `6ce86da`, controller `81c97d8`)
  - 수정 전용 `MemberUpdateVO`(name만) 사용. 이메일은 로그인 ID, 비밀번호 변경은 본인 확인이 필요해 JWT 이후로 미룸
  - 계층별로 나눠 커밋하고 각 단계마다 앱 기동 확인
  - curl로 검증: 조회 200·404, 수정 200(앞뒤 공백 제거, 같은 이름 재수정도 200)·404, 빈 이름/101자 400(fieldErrors), 이메일·비밀번호 필드 무시, 수정 후 기존 비밀번호로 로그인 200, id 형식 오류 400
  - 검증용 회원(`update-test@example.com`, id=16)이 DB에 남음. 회원 삭제 API가 없어 DB에서 직접 정리 필요
- 공연 삭제 409 처리: 예매 내역(취소된 예매 포함)이 있는 공연을 삭제하면 FK 위반으로 500이 나던 것을 `TicketService.deleteTicket`에서 `DataIntegrityViolationException`을 잡아 409로 변환 (`7d2743f`)
  - 사전 조회 대신 DB 제약에 맡겨 확인과 삭제 사이에 예매가 들어오는 경우에도 안전하게 처리. mapper 변경 없음
  - curl로 검증: 예매 있는 공연 409, 전부 취소된 공연도 409, 예매 없는 공연 204 후 조회 404, 없는 공연 404, 409 후 공연 데이터 유지
- JWT 인증 추가 (Spring Security + jjwt 0.12.6, HS256 액세스 토큰, STATELESS) (인프라 `3919a19`, Member `1f884b3`, Reservation `9ba2943`)
  - 인프라: `JwtTokenProvider`, `JwtAuthenticationFilter`, `SecurityConfig`, 401/403을 `ErrorResponse` JSON으로 응답하는 `JsonAuthErrorHandler`. 기본 사용자/임시 비밀번호 자동 구성은 제외
  - 비밀키는 환경변수 `JWT_SECRET`(기본값 없음, 32자 이상). 없거나 짧으면 기동 실패를 확인. 테스트는 `build.gradle.kts`에서 테스트 전용 값 주입. `application.yml`은 수정하지 않음
  - 로그인 응답이 `{accessToken, tokenType, expiresIn, member}`로 변경 (기존 클라이언트는 `member` 아래에서 읽어야 함)
  - 공개: 회원가입/로그인/공연 조회. 그 외 로그인 필요. 회원 단건 조회·수정은 본인만(403, 존재 여부 비노출을 위해 소유자 검사를 먼저 수행), 예매 취소는 본인 예매만(남의 예매는 404)
  - 예매의 `memberId`는 토큰에서 가져오고 요청 값은 무시. `GET /api/reservations`의 `memberId` 파라미터 제거
  - 검증: 토큰 없음/위조/만료/Bearer 아님 401, 유효 토큰 통과, 로그인 토큰 발급, 본인/타인 조회·수정(200/403), 위조 memberId 무시, 타인 예매 취소 404 후 수량 유지, 본인 취소 200·중복 취소 409. 동시성 테스트도 그대로 통과
  - 한계: 관리자 역할이 없어 공연 쓰기와 회원 목록은 로그인한 누구나 가능. 리프레시 토큰, 로그아웃(토큰 폐기) 없음. 응답 헤더/CORS 설정 없음
  - 검증용 데이터: 회원 `update-test@example.com`(id=16), `jwt-other@example.com`이 DB에 남음
- 정리 작업 (`babafeb`, `ea530d1`, `db4a8a1`)
  - `.gitignore` 끝의 깨진 줄(`.DS_Store` 뒤 UTF-16 `.idea/`, NUL 바이트 포함)을 제거하고 UTF-8로 `.DS_Store`, `.idea/`를 각각 새 줄로 추가. UTF-8/NUL 없음 확인. `git check-ignore -v .idea/workspace.xml`은 수정 전 `.idea/.gitignore:3:/workspace.xml`, 수정 후 `.gitignore:44:.idea/`에 매칭되고 `git status`의 `?? .idea/` 표시도 사라짐 (`babafeb`)
  - 회원 목록 API(`GET /api/members`) 삭제: 컨트롤러 → `MemberService.getMemberList` → `MemberMapper.selectMemberList` → XML 쿼리 순으로 다른 사용처가 없는 것을 확인하고 제거, 미사용 `List` import 정리. 모든 회원의 이메일이 로그인한 누구에게나 보이던 문제 해소 (`ea530d1`)
  - README: 환경변수 표 밖으로 떨어진 `JWT_SECRET` 행을 표 안으로 이동, 패키지 구조에 `SecurityConfig`·`JwtTokenProvider`·`JwtAuthenticationFilter`·`JsonAuthErrorHandler` 추가, 회원 목록 API 삭제를 API 표/구현 현황/인증 섹션에 반영 (`db4a8a1`)
  - IntelliJ에서 앱 기동, 로그인, 예매 확인 완료

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
