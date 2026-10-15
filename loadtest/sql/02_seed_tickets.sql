-- 부하 테스트용 공연 생성
-- 실행 대상: ticketo_loadtest 전용. 개발 DB(ticketo)에서 실행하지 않는다.
-- 반복 실행해도 안전하다 (같은 제목의 공연이 있으면 건너뛴다). 수량을 바꾸려면 아래 값을 고친 뒤
-- 99_cleanup.sql 로 지우고 다시 실행한다. 모든 시나리오는 quantity=1 로 예매한다.
-- 반드시 파일 전체를 한 세션에서 실행한다 (@변수 사용).
--
--   loadtest-hot         : 한 공연에 요청이 몰리는 경합 시나리오 (VU 수보다 작게 잡아 매진까지 간다)
--   loadtest-plenty      : 매진이 없는 처리량 시나리오 (재고가 부족해지지 않는다)
--   loadtest-spread-NNN  : 공연 여러 개(기본 200개, 각 100,000매)에 분산되는 시나리오 (행 락 경합이 적다)

USE ticketo_loadtest;

-- [안전장치] USE 이후 접속 DB가 ticketo_loadtest가 아니면 오류(Subquery returns more than 1 row)로 중단한다.
SELECT IF(DATABASE() = 'ticketo_loadtest', 1, (SELECT 1 UNION ALL SELECT 2)) AS guard_db;

-- 분산용 공연 수 / 공연당 수량
SET @spread_count = 200;
SET @spread_quantity = 100000;

-- 재귀 CTE 깊이 제한 (MySQL 기본값은 1000이라 @spread_count가 1000을 넘으면 필요하다)
SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO ticket (title, venue, event_at, price, total_quantity, remaining_quantity)
SELECT t.title, 'loadtest-venue', DATE_ADD(NOW(), INTERVAL 30 DAY), 10000, t.total, t.total
FROM (
    SELECT 'loadtest-hot' AS title, 500 AS total
    UNION ALL SELECT 'loadtest-plenty', 10000000
) t
WHERE NOT EXISTS (SELECT 1 FROM ticket x WHERE x.title = t.title);

INSERT INTO ticket (title, venue, event_at, price, total_quantity, remaining_quantity)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @spread_count
)
SELECT CONCAT('loadtest-spread-', LPAD(n, 3, '0')), 'loadtest-venue', DATE_ADD(NOW(), INTERVAL 30 DAY),
       10000, @spread_quantity, @spread_quantity
FROM seq
WHERE NOT EXISTS (SELECT 1 FROM ticket x WHERE x.title = CONCAT('loadtest-spread-', LPAD(n, 3, '0')));

SELECT CASE WHEN title LIKE 'loadtest-spread-%' THEN 'loadtest-spread-*' ELSE title END AS grp,
       COUNT(*) AS cnt, MIN(total_quantity) AS min_total, MAX(total_quantity) AS max_total
FROM ticket
WHERE title LIKE 'loadtest-%' AND title NOT LIKE 'loadtest-filler-%'
GROUP BY grp
ORDER BY grp;
