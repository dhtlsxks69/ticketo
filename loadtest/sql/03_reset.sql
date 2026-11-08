-- 부하 테스트 실행 전 초기화: 예매 내역을 지우고 공연 잔여 수량을 총 수량으로 되돌린다.
-- 실행 대상: ticketo_loadtest 전용. 회원/공연 행은 그대로 둔다 (공연 id가 바뀌지 않는다).
-- 매 실행(k6) 직전에 돌린다.

USE ticketo_loadtest;

-- [안전장치] USE 이후 접속 DB가 ticketo_loadtest가 아니면 오류(Subquery returns more than 1 row)로 중단한다.
SELECT IF(DATABASE() = 'ticketo_loadtest', 1, (SELECT 1 UNION ALL SELECT 2)) AS guard_db;

-- 아래 모든 DELETE/UPDATE는 loadtest- 접두어 데이터로만 범위를 제한한다.
DELETE FROM reservation
WHERE member_id IN (SELECT id FROM member WHERE email LIKE 'loadtest-%@example.com')
   OR ticket_id IN (SELECT id FROM ticket WHERE title LIKE 'loadtest-%');

-- filler는 예매 대상이 아니라 수량이 변하지 않으므로, 달라진 공연만 갱신한다.
UPDATE ticket
SET remaining_quantity = total_quantity
WHERE title LIKE 'loadtest-%'
  AND remaining_quantity <> total_quantity;

SELECT id, title, total_quantity, remaining_quantity
FROM ticket
WHERE title LIKE 'loadtest-%' AND title NOT LIKE 'loadtest-filler-%'
  AND title NOT LIKE 'loadtest-spread-%'
ORDER BY id;
SELECT COUNT(*) AS spread_not_reset FROM ticket
WHERE title LIKE 'loadtest-spread-%' AND remaining_quantity <> total_quantity;
SELECT COUNT(*) AS remaining_reservations FROM reservation;
