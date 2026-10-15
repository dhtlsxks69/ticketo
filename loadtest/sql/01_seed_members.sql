-- 부하 테스트용 회원 생성 (loadtest-00001@example.com ~ loadtest-NNNNN@example.com)
-- 실행 대상: ticketo_loadtest 전용. 개발 DB(ticketo)에서 실행하지 않는다.
-- 선행 조건: 부하용 앱에서 회원가입 API로 loadtest-seed@example.com 1명을 만들어 둘 것 (README 4단계).
--   이 스크립트는 그 회원의 BCrypt 해시를 복제하므로, 모든 부하 회원의 비밀번호는 시드 회원과 같다.
-- 반복 실행해도 안전하다 (이미 있는 이메일은 건너뛴다).
-- 반드시 파일 전체를 한 세션에서 실행한다 (@변수 사용).

USE ticketo_loadtest;

-- [안전장치] USE 이후 접속 DB가 ticketo_loadtest가 아니면 오류(Subquery returns more than 1 row)로 중단한다.
SELECT IF(DATABASE() = 'ticketo_loadtest', 1, (SELECT 1 UNION ALL SELECT 2)) AS guard_db;

-- 생성할 회원 수 (k6의 최대 VU 수 이상으로 맞춘다. 최대 99999)
SET @member_count = 100;

-- 시드 회원의 해시를 가져온다. 없으면 오류로 중단한다.
SET @pw_hash = NULL;
SELECT password INTO @pw_hash FROM member WHERE email = 'loadtest-seed@example.com';
SELECT IF(@pw_hash IS NULL, (SELECT 1 UNION ALL SELECT 2), 1) AS guard_seed_member;

SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO member (email, password, name)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @member_count
)
SELECT CONCAT('loadtest-', LPAD(n, 5, '0'), '@example.com'),
       @pw_hash,
       CONCAT('loadtest-', LPAD(n, 5, '0'))
FROM seq
ON DUPLICATE KEY UPDATE id = id;

SELECT COUNT(*) AS loadtest_members FROM member WHERE email LIKE 'loadtest-%@example.com';
