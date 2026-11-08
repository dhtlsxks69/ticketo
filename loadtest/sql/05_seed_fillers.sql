-- 조회 기준선 비교용 filler 공연 생성 (loadtest-filler-NNNNN)
-- 공연 테이블이 커졌을 때 목록/상세 조회 응답이 어떻게 달라지는지 비교하기 위한 채우기 데이터다.
-- filler는 예매 대상이 아니다. 이름 접두어(loadtest-filler-)로 구분하며, 예매 시나리오는 사용하지 않는다.
-- 실행 대상: ticketo_loadtest 전용. 개발 DB(ticketo)에서 실행하지 않는다.
-- 반복 실행해도 안전하다 (이미 만든 개수 이후 번호만 추가한다). 개수를 줄이려면 99_cleanup.sql 후 다시 실행한다. (늘리는 것은 변수만 키워 다시 실행하면 된다)
-- 반드시 파일 전체를 한 세션에서 실행한다 (@변수 사용).

USE ticketo_loadtest;

-- [안전장치] USE 이후 접속 DB가 ticketo_loadtest가 아니면 오류(Subquery returns more than 1 row)로 중단한다.
SELECT IF(DATABASE() = 'ticketo_loadtest', 1, (SELECT 1 UNION ALL SELECT 2)) AS guard_db;

-- 생성할 filler 공연 수 (예: 1000, 10000). 최대 99999
SET @filler_count = 1000;

SET SESSION cte_max_recursion_depth = 100000;

-- 이미 만든 filler 수. 번호는 1부터 연속으로만 만들어지므로 그 다음 번호부터 채운다.
-- (title에 인덱스가 없어 행마다 NOT EXISTS를 쓰면 10000개 규모에서 느려진다)
SELECT COUNT(*) INTO @filler_existing FROM ticket WHERE title LIKE 'loadtest-filler-%';

INSERT INTO ticket (title, venue, event_at, price, total_quantity, remaining_quantity)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @filler_count
)
SELECT CONCAT('loadtest-filler-', LPAD(n, 5, '0')), 'loadtest-venue',
       DATE_ADD(NOW(), INTERVAL (n % 365) + 1 DAY), 10000, 100, 100
FROM seq
WHERE n > @filler_existing;

SELECT COUNT(*) AS filler_tickets FROM ticket WHERE title LIKE 'loadtest-filler-%';
SELECT COUNT(*) AS total_tickets FROM ticket;
