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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 문제 재현 (1단계). 잔여 10매 공연에 스레드 100개가 1매씩 동시에 예매한다.
 *
 * 동시성 처리가 없는 reserve()는 "잔여 수량 조회 → 예매 저장 → 수량 차감" 사이에 다른 요청이 끼어들 수 있다.
 * 이 테스트는 결과를 출력만 하고 실패시키지 않는다. 해결(조건부 UPDATE, 비관적 락) 단계에서 검증식을 추가한다.
 *
 * 실제 DB(application.yml 접속 정보)를 사용하며, 테스트용 회원/공연/예매는 끝나면 삭제한다.
 */
@SpringBootTest
class ReservationConcurrencyTest {

    private static final int TOTAL_QUANTITY = 10;
    private static final int THREAD_COUNT = 100;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;
    private Long ticketId;

    @BeforeEach
    void setUp() {
        memberId = insertAndGetId(
                "INSERT INTO member (email, password, name) VALUES ('concurrency-test@example.com', 'x', 'concurrency')");
        ticketId = insertAndGetId(
                "INSERT INTO ticket (title, venue, event_at, price, total_quantity, remaining_quantity) "
                        + "VALUES ('concurrency-test', 'hall', '2099-01-01 19:00:00', 1000, "
                        + TOTAL_QUANTITY + ", " + TOTAL_QUANTITY + ")");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM reservation WHERE ticket_id = ?", ticketId);
        jdbcTemplate.update("DELETE FROM ticket WHERE id = ?", ticketId);
        jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
    }

    @Test
    void 잔여_10매에_100명이_동시에_예매하면_결과가_어긋나는지_확인한다() throws Exception {
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
                    reservationService.reserve(request);
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
        start.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        executor.shutdown();

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT remaining_quantity FROM ticket WHERE id = ?", Integer.class, ticketId);
        Integer reservedRows = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM reservation WHERE ticket_id = ? AND status = 'RESERVED'",
                Integer.class, ticketId);

        Map<String, Integer> errors = new TreeMap<>();
        otherErrors.forEach((k, v) -> errors.put(k, v.get()));

        System.out.println("========== 동시성 재현 결과 ==========");
        System.out.printf("총 수량=%d, 요청=%d%n", TOTAL_QUANTITY, THREAD_COUNT);
        System.out.printf("예매 성공=%d, 매진(409)=%d, 기타 오류=%s%n", success.get(), soldOut.get(), errors);
        System.out.printf("DB 잔여 수량=%d, DB RESERVED 수량 합계=%d%n", remaining, reservedRows);
        System.out.printf("기대값: 성공=%d, 잔여=0, RESERVED 합계=%d%n", TOTAL_QUANTITY, TOTAL_QUANTITY);
        boolean oversold = success.get() > TOTAL_QUANTITY || reservedRows > TOTAL_QUANTITY;
        boolean inconsistent = remaining == null || remaining < 0 || remaining + reservedRows != TOTAL_QUANTITY;
        System.out.printf("초과 판매=%s, 수량 불일치=%s, 기타 오류 발생=%s%n", oversold, inconsistent, !errors.isEmpty());
        System.out.println("======================================");

        // 재현 단계에서는 모든 스레드가 끝났는지만 확인한다.
        assertThat(success.get() + soldOut.get() + errors.values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(THREAD_COUNT);
    }

    private Long insertAndGetId(String sql) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
