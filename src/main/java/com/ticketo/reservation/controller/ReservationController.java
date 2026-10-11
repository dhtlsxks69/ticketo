package com.ticketo.reservation.controller;

import com.ticketo.reservation.service.ReservationService;
import com.ticketo.reservation.vo.ReservationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class ReservationController {

    private final ReservationService reservationService;

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
