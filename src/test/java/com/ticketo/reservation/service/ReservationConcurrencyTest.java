package com.ticketo.reservation.service;

import com.ticketo.reservation.vo.ReservationVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 테스트. 같은 조건(스레드 100개가 1매씩 동시에 예매)에서 예매 방식별 결과와 소요 시간을 비교한다.
 *
 * - 조건부 UPDATE: {@link ReservationService#reserve}
 * - 비관적 락: {@link ReservationService#reserveWithPessimisticLock}
 *
 * 결과는 출력만 하고 실패시키지 않는다. (모든 스레드가 끝났는지만 확인)
 * 실제 DB(application.yml 접속 정보)를 사용하며, 테스트용 회원/공연/예매는 끝나면 삭제한다.
 */
@SpringBootTest
class ReservationConcurrencyTest {

    private static final int THREAD_COUNT = 100;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;
    private final List<Long> ticketIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        memberId = insertAndGetId(
                "INSERT INTO member (email, password, name) VALUES ('concurrency-test@example.com', 'x', 'concurrency')");
    }

    @AfterEach
    void tearDown() {
        for (Long ticketId : ticketIds) {
            jdbcTemplate.update("DELETE FROM reservation WHERE ticket_id = ?", ticketId);
            jdbcTemplate.update("DELETE FROM ticket WHERE id = ?", ticketId);
        }
        jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
    }

    @Test
    void 조건부_UPDATE_방식() throws Exception {
        compare("조건부 UPDATE", reservationService::reserve);
    }

    @Test
    void 비관적_락_방식() throws Exception {
        compare("비관적 락", reservationService::reserveWithPessimisticLock);
    }

    /** 워밍업 1회(출력 없음) 후 잔여 10매(매진 발생), 잔여 100매(전원 성공) 두 경우를 측정한다. */
    private void compare(String label, Function<ReservationVO, ReservationVO> reserve) throws Exception {
        run(label, 10, reserve, false);
        run(label, 10, reserve, true);
        run(label, 100, reserve, true);
    }

    private void run(String label, int totalQuantity, Function<ReservationVO, ReservationVO> reserve, boolean print)
            throws Exception {
        Long ticketId = insertAndGetId(
                "INSERT INTO ticket (title, venue, event_at, price, total_quantity, remaining_quantity) "
                        + "VALUES ('concurrency-test', 'hall', '2099-01-01 19:00:00', 1000, "
                        + totalQuantity + ", " + totalQuantity + ")");
        ticketIds.add(ticketId);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        Map<String, AtomicInteger> otherErrors = new ConcurrentHashMap<>();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(executor.submit(() -> {
                ReservationVO request = new ReservationVO();
                request.setMemberId(memberId);
                request.setTicketId(ticketId);
                request.setQuantity(1);

                ready.countDown();
                try {
                    start.await(); // 모든 스레드가 준비될 때까지 기다렸다가 동시에 출발
                    reserve.apply(request);
                    success.incrementAndGet();
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode() == HttpStatus.CONFLICT) {
                        soldOut.incrementAndGet();
                    } else {
                        otherErrors.computeIfAbsent(e.getStatusCode().toString(), k -> new AtomicInteger())
                                .incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrors.computeIfAbsent(e.getClass().getSimpleName(), k -> new AtomicInteger())
                            .incrementAndGet();
                }
            }));
        }

        ready.await();
        long startedAt = System.nanoTime();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        executor.shutdown();

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT remaining_quantity FROM ticket WHERE id = ?", Integer.class, ticketId);
        Integer reservedRows = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM reservation WHERE ticket_id = ? AND status = 'RESERVED'",
                Integer.class, ticketId);

        Map<String, Integer> errors = new TreeMap<>();
        otherErrors.forEach((k, v) -> errors.put(k, v.get()));
        int expected = Math.min(totalQuantity, THREAD_COUNT);
        boolean oversold = success.get() > totalQuantity || reservedRows > totalQuantity;
        boolean inconsistent = remaining == null || remaining < 0 || remaining + reservedRows != totalQuantity;

        if (print) {
            System.out.printf("[결과] %s | 총=%d | 성공=%d(기대 %d) 매진=%d 기타오류=%s | 잔여=%d RESERVED=%d | "
                            + "초과판매=%s 불일치=%s | %dms%n",
                    label, totalQuantity, success.get(), expected, soldOut.get(), errors, remaining, reservedRows,
                    oversold, inconsistent, elapsedMs);
        }

        // 모든 스레드가 끝났는지만 확인한다.
        assertThat(success.get() + soldOut.get() + errors.values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(THREAD_COUNT);
    }

    private Long insertAndGetId(String sql) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keyHolder);
        return keyHolder.getKey().longValue();
    }
}
