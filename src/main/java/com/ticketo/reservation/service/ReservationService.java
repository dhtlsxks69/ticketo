package com.ticketo.reservation.service;

import com.ticketo.member.mapper.MemberMapper;
import com.ticketo.reservation.mapper.ReservationMapper;
import com.ticketo.reservation.vo.ReservationVO;
import com.ticketo.ticket.mapper.TicketMapper;
import com.ticketo.ticket.vo.TicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationMapper reservationMapper;
    private final TicketMapper ticketMapper;
    private final MemberMapper memberMapper;

    /** 회원의 예매 내역을 최신순으로 조회한다. 취소된 예매도 포함한다. */
    public List<ReservationVO> getReservationList(Long memberId) {
        if (memberMapper.selectMemberById(memberId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다. id=" + memberId);
        }
        return reservationMapper.selectReservationListByMemberId(memberId);
    }

    /**
     * 예매: 회원/공연 존재와 잔여 수량을 확인한 뒤 예매를 저장하고 잔여 수량을 차감한다.
     * 동시성 처리는 하지 않은 기본 버전이다. (조회 후 차감 사이에 다른 요청이 끼어들 수 있음)
     */
    @Transactional
    public ReservationVO reserve(ReservationVO reservation) {
        if (memberMapper.selectMemberById(reservation.getMemberId()) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "회원을 찾을 수 없습니다. id=" + reservation.getMemberId());
        }

        TicketVO ticket = ticketMapper.selectTicketById(reservation.getTicketId());
        if (ticket == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "공연을 찾을 수 없습니다. id=" + reservation.getTicketId());
        }
        if (ticket.getRemainingQuantity() < reservation.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "잔여 수량이 부족합니다. 잔여=" + ticket.getRemainingQuantity()
                            + ", 요청=" + reservation.getQuantity());
        }

        reservationMapper.insertReservation(reservation);
        ticketMapper.updateTicketRemainingQuantity(ticket.getId(), -reservation.getQuantity());
        return reservationMapper.selectReservationById(reservation.getId());
    }

    /**
     * 예매 취소: 예매 상태를 CANCELED로 바꾸고 예매했던 수량만큼 잔여 수량을 복구한다.
     * 이미 취소된 예매는 수량을 중복 복구하지 않도록 409로 거절한다.
     */
    @Transactional
    public ReservationVO cancel(Long id) {
        ReservationVO reservation = reservationMapper.selectReservationById(id);
        if (reservation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "예매를 찾을 수 없습니다. id=" + id);
        }

        // RESERVED 상태일 때만 갱신되므로, 0건이면 이미 취소된 예매다.
        if (reservationMapper.updateReservationCanceled(id) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 취소된 예매입니다. id=" + id);
        }
        ticketMapper.updateTicketRemainingQuantity(reservation.getTicketId(), reservation.getQuantity());
        return reservationMapper.selectReservationById(id);
    }
}
