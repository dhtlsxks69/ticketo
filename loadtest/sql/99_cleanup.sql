-- 부하 테스트 데이터 삭제 (filler 포함, 예매 -> 공연 -> 회원 순서, FK 때문).
-- 실행 대상: ticketo_loadtest 전용. 시드 회원(loadtest-seed@example.com)도 함께 지워진다.
-- DB 자체를 없애려면 별도로 DROP DATABASE ticketo_loadtest; 를 직접 실행한다.

USE ticketo_loadtest;

-- [안전장치] USE 이후 접속 DB가 ticketo_loadtest가 아니면 오류(Subquery returns more than 1 row)로 중단한다.
SELECT IF(DATABASE() = 'ticketo_loadtest', 1, (SELECT 1 UNION ALL SELECT 2)) AS guard_db;

-- 모든 DELETE는 loadtest- 접두어 데이터로만 범위를 제한한다. (공연 삭제의 title 조건은 filler도 포함)
DELETE FROM reservation
WHERE member_id IN (SELECT id FROM member WHERE email LIKE 'loadtest-%@example.com')
   OR ticket_id IN (SELECT id FROM ticket WHERE title LIKE 'loadtest-%');
DELETE FROM ticket WHERE title LIKE 'loadtest-%';
DELETE FROM member WHERE email LIKE 'loadtest-%@example.com';
