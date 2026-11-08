-- 부하 테스트 실행 후 정합성 검증 (읽기 전용).
-- 실행 대상: ticketo_loadtest 전용.
-- 합격 기준(예매 대상 공연 전체, filler 제외): 불일치/초과 판매 목록이 0행이고, 마지막 요약의 mismatch_tickets = 0, oversold_tickets = 0.
--   consistent : 잔여 수량 == 총 수량 - 유효(RESERVED) 예매 수량 합  (수량 유실/중복 차감이 없다)
--   oversold   : 유효 예매 수량 합이 총 수량을 넘었는가 (초과 판매)

USE ticketo_loadtest;

-- [안전장치] USE 이후 접속 DB가 ticketo_loadtest가 아니면 오류(Subquery returns more than 1 row)로 중단한다.
SELECT IF(DATABASE() = 'ticketo_loadtest', 1, (SELECT 1 UNION ALL SELECT 2)) AS guard_db;

-- 불일치(consistent = 0) 또는 초과 판매(oversold = 1)인 공연만 출력한다. 0행이면 통과.
SELECT * FROM (
    SELECT t.id,
           t.title,
           t.total_quantity,
           t.remaining_quantity,
           COALESCE(r.reserved_qty, 0)                                             AS reserved_qty,
           COALESCE(r.reserved_cnt, 0)                                             AS reserved_cnt,
           (t.remaining_quantity = t.total_quantity - COALESCE(r.reserved_qty, 0)) AS consistent,
           (COALESCE(r.reserved_qty, 0) > t.total_quantity)                        AS oversold
    FROM ticket t
    LEFT JOIN (
        SELECT ticket_id, SUM(quantity) AS reserved_qty, COUNT(*) AS reserved_cnt
        FROM reservation
        WHERE status = 'RESERVED'
        GROUP BY ticket_id
    ) r ON r.ticket_id = t.id
    WHERE t.title LIKE 'loadtest-%'
      AND t.title NOT LIKE 'loadtest-filler-%'   -- filler는 예매 대상이 아니라 따로 확인한다
) v
WHERE v.consistent = 0 OR v.oversold = 1
ORDER BY v.id;

-- filler 공연이 예매되지 않았는지 확인: 모두 0이어야 한다 (예매가 있으면 filler_reserved > 0, 수량이 변했으면 filler_changed > 0)
SELECT COUNT(*) AS filler_tickets,
       SUM(t.remaining_quantity <> t.total_quantity) AS filler_changed,
       (SELECT COUNT(*) FROM reservation r JOIN ticket f ON f.id = r.ticket_id
         WHERE f.title LIKE 'loadtest-filler-%') AS filler_reserved
FROM ticket t
WHERE t.title LIKE 'loadtest-filler-%';

-- 상태별 예매 건수 (취소 시나리오를 쓴 경우 CANCELED도 나온다)
SELECT status, COUNT(*) AS cnt, SUM(quantity) AS qty FROM reservation GROUP BY status;

-- quantity=1 고정 확인: 1이 아닌 예매가 있으면 0건이 아니다
SELECT COUNT(*) AS non_one_quantity FROM reservation WHERE quantity <> 1;

-- 전체 요약 (한 줄). mismatch_tickets = 0 이고 oversold_tickets = 0 이어야 통과.
-- sum_total - sum_remaining 은 sum_reserved 와 같아야 한다.
SELECT COUNT(*)                          AS ticket_count,
       COALESCE(SUM(v.consistent = 0), 0) AS mismatch_tickets,
       COALESCE(SUM(v.oversold = 1), 0)   AS oversold_tickets,
       SUM(v.total_quantity)             AS sum_total,
       SUM(v.remaining_quantity)         AS sum_remaining,
       SUM(v.reserved_qty)               AS sum_reserved
FROM (
    SELECT t.id,
           t.title,
           t.total_quantity,
           t.remaining_quantity,
           COALESCE(r.reserved_qty, 0)                                             AS reserved_qty,
           COALESCE(r.reserved_cnt, 0)                                             AS reserved_cnt,
           (t.remaining_quantity = t.total_quantity - COALESCE(r.reserved_qty, 0)) AS consistent,
           (COALESCE(r.reserved_qty, 0) > t.total_quantity)                        AS oversold
    FROM ticket t
    LEFT JOIN (
        SELECT ticket_id, SUM(quantity) AS reserved_qty, COUNT(*) AS reserved_cnt
        FROM reservation
        WHERE status = 'RESERVED'
        GROUP BY ticket_id
    ) r ON r.ticket_id = t.id
    WHERE t.title LIKE 'loadtest-%'
      AND t.title NOT LIKE 'loadtest-filler-%'   -- filler는 예매 대상이 아니라 따로 확인한다
) v;
