package com.ticketo.reservation.controller;

import com.ticketo.reservation.service.ReservationService;
import com.ticketo.reservation.vo.ReservationVO;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Slf4j
public class ReservationController {

    private static final String CONDITIONAL_UPDATE = "conditional-update";
    private static final String PESSIMISTIC = "pessimistic";

    private final ReservationService reservationService;

    /** 예매 락 전략 (conditional-update | pessimistic). 부하 테스트 비교용 설정이다. */
    @Value("${ticketo.reservation.lock-strategy:conditional-update}")
    private String lockStrategy;

    /** 알 수 없는 값은 오타로 조용히 기본값이 되지 않도록 기동 시 실패시킨다. */
    @PostConstruct
    void validateLockStrategy() {
        if (!CONDITIONAL_UPDATE.equals(lockStrategy) && !PESSIMISTIC.equals(lockStrategy)) {
            throw new IllegalArgumentException("알 수 없는 예매 락 전략: " + lockStrategy
                    + " (conditional-update 또는 pessimistic)");
        }
        log.info("예매 락 전략: {}", lockStrategy);
    }

    /** 내 예매 내역 조회 (로그인 필요, 최신순, 취소된 예매 포함) */
    @GetMapping
    public List<ReservationVO> list(@AuthenticationPrincipal Long loginMemberId) {
        return reservationService.getReservationList(loginMemberId);
    }

    /** 예매 (로그인 필요, 잔여 수량 차감). 예매한 회원은 토큰에서 가져온다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationVO reserve(@AuthenticationPrincipal Long loginMemberId,
            @Valid @RequestBody ReservationVO reservation) {
        reservation.setMemberId(loginMemberId);
        if (PESSIMISTIC.equals(lockStrategy)) {
            return reservationService.reserveWithPessimisticLock(reservation);
        }
        return reservationService.reserve(reservation);
    }

    /**
     * 예매 취소 (로그인 필요, 본인 예매만, 잔여 수량 복구).
     * 예매를 삭제하지 않고 상태를 CANCELED로 바꿔 결과를 반환한다.
     */
    @DeleteMapping("/{id}")
    public ReservationVO cancel(@PathVariable Long id, @AuthenticationPrincipal Long loginMemberId) {
        return reservationService.cancel(id, loginMemberId);
    }
}
