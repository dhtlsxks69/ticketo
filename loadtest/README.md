# 부하 테스트 런북

예매 API(`POST /api/reservations`)를 k6로 부하 테스트하기 위한 환경 준비 절차입니다.
이 문서는 **환경 준비(1단계)** 까지만 다룹니다. k6 스크립트와 실행 절차는 다음 단계에서 추가합니다.

## 원칙

- 부하 테스트는 개발 DB(`ticketo`)가 아니라 **전용 DB `ticketo_loadtest`** 에서만 한다.
- 개발 DB용 실행 설정(`TicketoApplication`)은 **수정하지 않는다.** 부하용은 복제한 별도 설정(`Ticketo-loadtest`)만 쓴다.
- 모든 시나리오는 예매 수량 **`quantity=1` 로 고정**한다. (수량 합계 검증을 `예매 건수 = 차감 수량`으로 단순하게 하기 위해)
- SQL은 IntelliJ Database 도구나 MySQL 클라이언트(대화형)에서 실행한다. PowerShell에서는 `mysql < file.sql` 리다이렉션이 동작하지 않으므로 쓰지 않는다.
- 모든 SQL 스크립트 첫머리에 접속 DB가 `ticketo_loadtest`가 아니면 오류로 멈추는 안전장치가 있다. 오류가 나면 IntelliJ의 오류 대화상자에서 **중단(Stop)** 을 선택한다.

## 파일 구성

| 경로 | 설명 |
|---|---|
| `sql/01_seed_members.sql` | 부하용 회원 생성 (`loadtest-00001@example.com` ~, 기본 100명). 시드 회원의 BCrypt 해시를 복제 |
| `sql/02_seed_tickets.sql` | 부하용 공연 생성 (`loadtest-hot`, `loadtest-plenty`, `loadtest-spread-001~200`) |
| `sql/03_reset.sql` | 실행 전 초기화 (예매 삭제, 잔여 수량 복구). 매 실행 직전에 사용 |
| `sql/04_verify.sql` | 실행 후 정합성 검증 (읽기 전용). filler는 제외하고 예매·수량 변동이 없는지만 확인 |
| `sql/05_seed_fillers.sql` | 조회 기준선 비교용 filler 공연 생성 (`loadtest-filler-*`, 개수는 스크립트 맨 위 `@filler_count`). 선택 사항 |
| `sql/99_cleanup.sql` | 부하 데이터 전체 삭제 (filler 포함) |
| `results/` | k6 결과 출력 위치 (`.gitignore`로 커밋 제외) |

## 공연 구성

| 공연 제목 | 총 수량 | 목적 |
|---|---|---|
| `loadtest-hot` | 500 | 한 공연에 요청이 몰리는 경합. VU 수·시간에 비해 작아서 매진(409)까지 간다 |
| `loadtest-plenty` | 10,000,000 | 매진 없는 처리량 측정 |
| `loadtest-spread-001~200` | 각 100,000 | 여러 공연에 분산되는 경우 (행 락 경합이 적은 기준선). 개수는 `@spread_count` |
| `loadtest-filler-*` | 각 100 | 공연 테이블 크기에 따른 조회 응답 비교용 채우기. **예매 대상이 아니다.** 선택 사항(`05_seed_fillers.sql`) |

수량·개수는 각 SQL 파일 상단의 변수(`@member_count`, `@spread_count`, `@spread_quantity`, `@filler_count`)와 `loadtest-hot`/`loadtest-plenty` 값을 고쳐 조정한다.
공연 수량을 바꿀 때는 `99_cleanup.sql` 로 지운 뒤 `02_seed_tickets.sql` 을 다시 실행한다.

## 준비 절차

### 1. 전용 DB 만들기

IntelliJ Database 도구의 콘솔(또는 MySQL 클라이언트 프롬프트)에서 한 번만 실행한다.

```sql
CREATE DATABASE IF NOT EXISTS ticketo_loadtest DEFAULT CHARACTER SET utf8mb4;
```

앱에서 쓰는 DB 계정(`DB_USERNAME`)이 이 DB에 대한 권한(CREATE, SELECT, INSERT, UPDATE, DELETE)을 갖고 있어야 한다.
권한이 없으면 관리자 계정으로 `GRANT ... ON ticketo_loadtest.* TO ...` 를 먼저 실행한다.

IntelliJ Database 도구에는 `ticketo_loadtest`를 가리키는 **별도 데이터 소스**를 하나 만들어 둔다. (SQL 실행 시 접속 DB를 헷갈리지 않기 위해)

### 2. 부하용 실행 설정 만들기 (`Ticketo-loadtest`)

1. IntelliJ 상단 Run/Debug 설정 드롭다운 → **Edit Configurations...**
2. 왼쪽에서 `TicketoApplication` 선택 → 상단 **Copy Configuration**(복제) 아이콘 클릭
3. 이름을 `Ticketo-loadtest` 로 바꾼다.
4. **Environment variables** 에 기존 값(`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`)을 그대로 두고 `DB_NAME=ticketo_loadtest` 를 추가한다.
   - 예: `DB_NAME=ticketo_loadtest;DB_USERNAME=...;DB_PASSWORD=...;JWT_SECRET=...`
5. 원본 `TicketoApplication` 설정은 **건드리지 않는다.** 개발용 앱은 계속 `ticketo` DB를 본다.

`application.yml`의 `DB_NAME` 기본값이 `ticketo` 이므로, `DB_NAME` 환경변수만 바꾸면 접속 DB가 바뀐다. 코드·설정 파일 수정은 필요 없다.

### 3. 앱을 한 번 띄워 테이블 만들기

`Ticketo-loadtest` 설정으로 앱을 실행한다. `spring.sql.init.mode=always` 이므로 기동 시 `schema.sql`이
`ticketo_loadtest`에 `member`, `ticket`, `reservation` 테이블을 만든다. (별도 DDL 작업은 하지 않는다)

기동 로그에 오류가 없는 것을 확인하고, 데이터 소스(`ticketo_loadtest`)에서 테이블 3개가 생겼는지 확인한다.

```sql
SHOW TABLES;
```

앱은 4단계 회원가입에 필요하므로 켜 둔다.

### 4. 시드 회원 1명을 회원가입 API로 만들기

BCrypt 해시를 직접 만들지 않고, 실제 앱이 만든 해시를 복제한다. 부하용 앱(`Ticketo-loadtest`)이 떠 있는 상태에서 PowerShell로 호출한다.

```powershell
$body = @{ email = "loadtest-seed@example.com"; password = "loadtest1234"; name = "loadtest-seed" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/members -ContentType "application/json; charset=utf-8" -Body $body
```

- 이 이메일·비밀번호(`loadtest-seed@example.com` / `loadtest1234`)는 `01_seed_members.sql`이 찾는 값이므로 바꾸지 않는다.
- 이후 만들어지는 모든 부하 회원(`loadtest-00001@example.com` ~)의 비밀번호는 `loadtest1234` 이다.
- 이미 가입된 경우 409 등으로 응답할 수 있다. 데이터 소스에서 `SELECT email, LEFT(password, 7) FROM member WHERE email = 'loadtest-seed@example.com';` 로 존재와 해시 접두어(`$2a$10$` 등)를 확인한다.

### 5. 데이터 넣기

`ticketo_loadtest` 데이터 소스에서 아래 순서로 **파일 전체**를 실행한다.
(IntelliJ: 파일을 열고 해당 데이터 소스를 선택한 뒤 **Execute**(Ctrl+Enter 또는 Ctrl+Shift+F10). 선택 영역이 없으면 파일 전체가 한 세션으로 실행된다.)

IntelliJ를 쓰지 않거나 `mysql` 클라이언트가 설치돼 있지 않을 때:

- **MySQL Workbench**: File > Open SQL Script로 파일을 열고, 번개(⚡) 아이콘으로 전체를 실행한다.
- **DBeaver**: 새 SQL 스크립트에 파일 내용을 열고 Alt+X(스크립트 실행)로 전체를 한 세션에서 실행한다.
- **mysql 명령줄**: PowerShell에서도 `-e`로 `source`를 넘기면 동작한다. 한글 주석·데이터가 깨지지 않도록 반드시 `--default-character-set=utf8mb4` 를 붙이고, 경로는 슬래시(`/`)로 쓴다.

  ```powershell
  mysql --default-character-set=utf8mb4 -u <계정> -p -e "source C:/Users/user-pc/IdeaProjects/ticketo/loadtest/sql/01_seed_members.sql"
  ```

  대화형 프롬프트로 들어가 실행할 때도 `mysql --default-character-set=utf8mb4 -u <계정> -p` 로 접속한 뒤 `source 경로;` 를 쓴다.

1. `sql/01_seed_members.sql` — 회원 생성. 마지막 결과의 `loadtest_members` 가 101(`@member_count` 100 + 시드 회원 1)인지 확인
2. `sql/02_seed_tickets.sql` — 공연 생성. 마지막 결과에서 `loadtest-hot` 1, `loadtest-plenty` 1, `loadtest-spread-*` 200개인지 확인
3. (선택) `sql/05_seed_fillers.sql` — 조회 기준선 비교용 filler 공연. 맨 위 `@filler_count`(예: 1000, 10000)를 정한 뒤 실행. 늘릴 때는 값만 키워 다시 실행하면 된다

세 스크립트 모두 반복 실행해도 안전하다(이미 있으면 건너뜀). 모든 SQL 파일의 첫 문장은 `USE ticketo_loadtest;` 이고 바로 뒤에 접속 DB 확인 쿼리가 있다.

### 6. 매 실행 루프

```
03_reset.sql 실행 → k6 실행(다음 단계) → 04_verify.sql 실행
```

`04_verify.sql` 합격 기준: 불일치/초과 판매 공연 목록이 **0행**이고(예매 대상 공연만, filler 제외), 마지막 요약 한 줄의 `mismatch_tickets = 0`, `oversold_tickets = 0`, 그리고 `filler_changed = 0`, `filler_reserved = 0`, `non_one_quantity = 0`.

공연이 `loadtest-hot`처럼 매진되는 시나리오에서는 `reserved_qty` 가 `total_quantity` 와 같아지고, 나머지 요청은 409(잔여 수량 부족)로 끝나는 것이 정상이다.

### 7. 정리

`sql/99_cleanup.sql` 로 부하 데이터를 지운다. DB째 없애려면 데이터 소스에서 직접 `DROP DATABASE ticketo_loadtest;` 를 실행한다.

## 알려 둘 점 (다음 단계에서 반영)

- 로그인은 BCrypt 검증이라 CPU를 많이 쓴다. k6에서는 VU마다 로그인을 반복하지 말고 시작 시 한 번만 로그인해 토큰을 재사용한다. (액세스 토큰 유효 시간 `expiresIn` 은 로그인 응답에 있음. 기본 3600초)
- 예매 컨트롤러는 현재 조건부 UPDATE 버전(`reserve`)을 호출한다. 비관적 락 버전(`reserveWithPessimisticLock`) 비교는 컨트롤러 변경이 필요하며 다음 단계에서 다룬다.
- 앱과 k6, MySQL이 같은 PC에서 돌면 서로 자원을 나눠 쓰므로 절대 수치보다 방식 간 상대 비교로 해석한다.
